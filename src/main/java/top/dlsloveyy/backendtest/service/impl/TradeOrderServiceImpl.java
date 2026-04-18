package top.dlsloveyy.backendtest.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.dlsloveyy.backendtest.entity.CustomerServiceCase;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.TradeDispute;
import top.dlsloveyy.backendtest.entity.TradeOrder;
import top.dlsloveyy.backendtest.entity.User; // 🚀 引入 User 实体
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.TradeOrderMapper;
import top.dlsloveyy.backendtest.model.dto.OrderCreateDTO;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.model.vo.TradeOrderVO;
import top.dlsloveyy.backendtest.service.CustomerServiceCaseService;
import top.dlsloveyy.backendtest.service.OperationAuditLogService;
import top.dlsloveyy.backendtest.service.TradeDisputeService;
import top.dlsloveyy.backendtest.service.TradeOrderService;
import top.dlsloveyy.backendtest.service.UserService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static top.dlsloveyy.backendtest.constant.CustomerServiceCaseStatus.IN_PROGRESS;
import static top.dlsloveyy.backendtest.constant.CustomerServiceCaseStatus.PENDING_ASSIGN;
import static top.dlsloveyy.backendtest.constant.CustomerServiceCaseStatus.WAITING_EVIDENCE;
import static top.dlsloveyy.backendtest.constant.DisputeStatus.CLOSED;
import static top.dlsloveyy.backendtest.constant.DisputeStatus.IN_REVIEW;
import static top.dlsloveyy.backendtest.constant.DisputeStatus.OPEN;
import static top.dlsloveyy.backendtest.constant.OrderStatus.*;

@Service
public class TradeOrderServiceImpl extends ServiceImpl<TradeOrderMapper, TradeOrder> implements TradeOrderService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private CustomerServiceCaseService customerServiceCaseService;

    @Autowired
    private OperationAuditLogService operationAuditLogService;

    @Autowired
    private TradeDisputeService tradeDisputeService;

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.05");
    private static final long PAYMENT_TIMEOUT_MINUTES = 15L;

    /**
     * 创建订单：适配多库存逻辑 + Redis 防超卖锁 + 数据库事务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createOrder(OrderCreateDTO dto, Long currentUserId) {
        Long goodsId = dto.getGoodsId();
        String lockKey = "lock:goods:" + goodsId;
        Boolean isLocked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", 10, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(isLocked)) {
            return ResponseResult.error("商品太火爆啦，正在被别人抢购中，请稍后再试！");
        }

        try {
            Goods goods = goodsMapper.selectById(goodsId);
            if (goods == null) {
                return ResponseResult.error("商品不存在！");
            }
            if (goods.getStock() <= 0) {
                return ResponseResult.error("手慢了，该商品已售罄！");
            }
            if (goods.getStatus() != 1) {
                return ResponseResult.error("该商品目前不可购买！");
            }
            if (goods.getSellerId().equals(currentUserId)) {
                return ResponseResult.error("不能购买自己发布的商品哦！");
            }

            BigDecimal orderPrice = goods.getPrice();
            BigDecimal platformFee = orderPrice.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal sellerIncome = orderPrice.subtract(platformFee);

            TradeOrder order = new TradeOrder();
            String orderNo = "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
            order.setOrderNo(orderNo);
            order.setBuyerId(currentUserId);
            order.setSellerId(goods.getSellerId());
            order.setGoodsId(goodsId);
            order.setOrderPrice(orderPrice);
            order.setPlatformFee(platformFee);
            order.setSellerIncome(sellerIncome);
            order.setDeliveryMethod(dto.getDeliveryMethod());
            order.setDeliveryAddress(dto.getDeliveryAddress());
            order.setStatus(PENDING_PAYMENT);
            order.setCreateTime(LocalDateTime.now());

            tradeOrderMapper.insert(order);

            operationAuditLogService.log(
                    currentUserId,
                    "USER",
                    "ORDER_CREATED",
                    "TRADE_ORDER",
                    String.valueOf(order.getId()),
                    "goodsId=" + goodsId + ", status=" + PENDING_PAYMENT
            );

            goods.setStock(goods.getStock() - 1);
            if (goods.getStock() == 0) {
                goods.setStatus(3);
            }
            goodsMapper.updateById(goods);

            return ResponseResult.success("订单创建成功", order.getId());

        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    @Override
    public ResponseResult<List<TradeOrderVO>> getMyPurchases(Long buyerId) {
        List<TradeOrderVO> list = tradeOrderMapper.selectMyPurchases(buyerId);
        return ResponseResult.success(list);
    }

    @Override
    public ResponseResult<List<TradeOrderVO>> getMySales(Long sellerId) {
        List<TradeOrderVO> list = tradeOrderMapper.selectMySales(sellerId);
        return ResponseResult.success(list);
    }

    // ==================== 支付核心逻辑 (双渠道) ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> payOrder(Long orderId, Long buyerId, String paymentMethod) {
        TradeOrder order = this.getById(orderId);
        if (order == null) {
            return ResponseResult.error("订单不存在");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            return ResponseResult.error("无权操作此订单");
        }
        if (order.getStatus() != PENDING_PAYMENT) {
            return ResponseResult.error("订单当前状态无法支付");
        }

        String normalizedMethod = paymentMethod == null ? "WECHAT" : paymentMethod.trim().toUpperCase(Locale.ROOT);
        ResponseResult<?> riskDecision = evaluatePaymentRisk(order, buyerId, normalizedMethod);
        if (riskDecision != null) {
            return riskDecision;
        }

        operationAuditLogService.log(
                buyerId,
                "USER",
                "ORDER_PAY_ATTEMPT",
                "TRADE_ORDER",
                String.valueOf(orderId),
                "paymentMethod=" + normalizedMethod
        );

        if ("BALANCE".equals(normalizedMethod)) {
            User buyer = userService.getById(buyerId);
            BigDecimal currentBalance = buyer.getBalance() != null ? buyer.getBalance() : BigDecimal.ZERO;
            if (currentBalance.compareTo(order.getOrderPrice()) < 0) {
                return ResponseResult.error("账户余额不足，请充值或使用微信/支付宝支付");
            }
            buyer.setBalance(currentBalance.subtract(order.getOrderPrice()));
            userService.updateById(buyer);
        } else {
            if (!"WECHAT".equals(normalizedMethod) && !"ALIPAY".equals(normalizedMethod)) {
                return ResponseResult.error("不支持的支付方式");
            }
            order.setPaymentNo("SIMULATE_" + normalizedMethod + "_" + System.currentTimeMillis());
        }

        order.setStatus(PAID_PENDING_SHIPMENT);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        this.updateById(order);

        operationAuditLogService.log(
                buyerId,
                "USER",
                "ORDER_PAID",
                "TRADE_ORDER",
                String.valueOf(orderId),
                "status=" + PAID_PENDING_SHIPMENT + ", paymentMethod=" + normalizedMethod
        );

        return ResponseResult.success("支付成功，已通知卖家发货");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> shipOrder(Long orderId, Long sellerId) {
        TradeOrder order = this.getById(orderId);
        if (order == null) {
            return ResponseResult.error("订单不存在");
        }
        if (!order.getSellerId().equals(sellerId)) {
            return ResponseResult.error("无权操作此订单");
        }
        if (order.getStatus() != PAID_PENDING_SHIPMENT) {
            return ResponseResult.error("订单当前状态无法发货");
        }

        order.setStatus(SHIPPED_PENDING_RECEIPT);
        order.setDeliveryTime(LocalDateTime.now()); // 记录发货时间
        order.setUpdateTime(LocalDateTime.now());
        this.updateById(order);

        operationAuditLogService.log(
                sellerId,
                "USER",
                "ORDER_SHIPPED",
                "TRADE_ORDER",
                String.valueOf(orderId),
                "status=" + SHIPPED_PENDING_RECEIPT
        );

        return ResponseResult.success("发货成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> receiveOrder(Long orderId, Long buyerId) {
        TradeOrder order = this.getById(orderId);
        if (order == null) {
            return ResponseResult.error("订单不存在");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            return ResponseResult.error("无权操作此订单");
        }
        if (order.getStatus() != SHIPPED_PENDING_RECEIPT) {
            return ResponseResult.error("订单当前状态无法确认收货");
        }

        order.setStatus(COMPLETED);
        LocalDateTime now = LocalDateTime.now();
        order.setUpdateTime(now);
        order.setFinishTime(now);
        this.updateById(order);

        try {
            userService.increaseBalance(order.getSellerId(), order.getSellerIncome());
        } catch (Exception e) {
            throw new RuntimeException("资金结算异常，确认收货失败: " + e.getMessage());
        }

        operationAuditLogService.log(
                buyerId,
                "USER",
                "ORDER_RECEIVED",
                "TRADE_ORDER",
                String.valueOf(orderId),
                "status=" + COMPLETED
        );

        return ResponseResult.success("交易完成，钱款已汇入卖家账户！");
    }

    // ==================== 退款核心逻辑 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> applyRefund(Long orderId, Long buyerId, String reason, String buyerEvidence) {
        TradeOrder order = this.getById(orderId);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            return ResponseResult.error("无权操作或订单不存在");
        }

        if (order.getStatus() == PAID_PENDING_SHIPMENT) {
            order.setStatus(REFUNDED);
            order.setUpdateTime(LocalDateTime.now());
            this.updateById(order);

            restoreGoodsStock(order.getGoodsId());

            try {
                userService.increaseBalance(order.getBuyerId(), order.getOrderPrice());
            } catch (Exception e) {
                throw new RuntimeException("退款失败：资金退还异常 - " + e.getMessage());
            }

            operationAuditLogService.log(
                    buyerId,
                    "USER",
                    "ORDER_REFUNDED_DIRECT",
                    "TRADE_ORDER",
                    String.valueOf(orderId),
                    "status=" + REFUNDED
            );
            return ResponseResult.success("退款成功，资金已原路返回");
        }

        if (order.getStatus() == SHIPPED_PENDING_RECEIPT) {
            return tradeDisputeService.createDispute(orderId, buyerId, reason, buyerEvidence);
        }

        return ResponseResult.error("当前订单状态不支持退款");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> approveRefund(Long orderId, Long sellerId) {
        TradeOrder order = this.getById(orderId);
        if (order == null || !order.getSellerId().equals(sellerId)) {
            return ResponseResult.error("无权操作");
        }
        if (order.getStatus() != REFUND_REQUESTED) {
            return ResponseResult.error("该订单未申请退款");
        }
        boolean csHandling = customerServiceCaseService.lambdaQuery()
                .eq(CustomerServiceCase::getOrderId, orderId)
                .in(CustomerServiceCase::getStatus, PENDING_ASSIGN, IN_PROGRESS, WAITING_EVIDENCE)
                .exists();
        if (csHandling) {
            return ResponseResult.error("该退款争议已进入客服处理流程，卖家不可再次操作");
        }

        order.setStatus(REFUNDED);
        order.setUpdateTime(LocalDateTime.now());
        this.updateById(order);
        restoreGoodsStock(order.getGoodsId());

        try {
            userService.increaseBalance(order.getBuyerId(), order.getOrderPrice());
        } catch (Exception e) {
            throw new RuntimeException("退款失败：资金退还异常 - " + e.getMessage());
        }

        operationAuditLogService.log(
                sellerId,
                "USER",
                "ORDER_REFUND_APPROVED",
                "TRADE_ORDER",
                String.valueOf(orderId),
                "status=" + REFUNDED
        );

        return ResponseResult.success("已同意退款，资金已退回买家账户");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> rejectRefund(Long orderId, Long sellerId) {
        TradeOrder order = this.getById(orderId);
        if (order == null || !order.getSellerId().equals(sellerId)) {
            return ResponseResult.error("无权操作");
        }
        if (order.getStatus() != REFUND_REQUESTED) {
            return ResponseResult.error("该订单未申请退款");
        }
        boolean csHandling = customerServiceCaseService.lambdaQuery()
                .eq(CustomerServiceCase::getOrderId, orderId)
                .in(CustomerServiceCase::getStatus, PENDING_ASSIGN, IN_PROGRESS, WAITING_EVIDENCE)
                .exists();
        if (csHandling) {
            return ResponseResult.error("该退款争议已进入客服处理流程，卖家不可再次操作");
        }

        order.setStatus(SHIPPED_PENDING_RECEIPT);
        order.setUpdateTime(LocalDateTime.now());
        this.updateById(order);

        TradeDispute activeDispute = tradeDisputeService.getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TradeDispute>()
                .eq(TradeDispute::getOrderId, orderId)
                .in(TradeDispute::getStatus, OPEN, IN_REVIEW)
                .orderByDesc(TradeDispute::getCreateTime)
                .last("limit 1"));
        if (activeDispute != null) {
            activeDispute.setStatus(CLOSED);
            activeDispute.setResolution("SELLER_REJECTED_REFUND");
            activeDispute.setUpdateTime(LocalDateTime.now());
            activeDispute.setCloseTime(LocalDateTime.now());
            tradeDisputeService.updateById(activeDispute);
        }

        operationAuditLogService.log(
                sellerId,
                "USER",
                "ORDER_REFUND_REJECTED",
                "TRADE_ORDER",
                String.valueOf(orderId),
                "status=" + SHIPPED_PENDING_RECEIPT
        );

        return ResponseResult.success("已拒绝退款申请，买家可申请客服介入");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int closeExpiredPendingOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(PAYMENT_TIMEOUT_MINUTES);
        List<TradeOrder> expiredOrders = this.lambdaQuery()
                .eq(TradeOrder::getStatus, PENDING_PAYMENT)
                .lt(TradeOrder::getCreateTime, deadline)
                .list();

        int closedCount = 0;
        for (TradeOrder order : expiredOrders) {
            boolean updated = this.lambdaUpdate()
                    .eq(TradeOrder::getId, order.getId())
                    .eq(TradeOrder::getStatus, PENDING_PAYMENT)
                    .set(TradeOrder::getStatus, CLOSED)
                    .set(TradeOrder::getUpdateTime, LocalDateTime.now())
                    .update();
            if (!updated) {
                continue;
            }

            restoreGoodsStock(order.getGoodsId());
            operationAuditLogService.log(
                    order.getBuyerId(),
                    "SYSTEM",
                    "ORDER_AUTO_CLOSED_TIMEOUT",
                    "TRADE_ORDER",
                    String.valueOf(order.getId()),
                    "status=" + CLOSED + ", timeoutMinutes=" + PAYMENT_TIMEOUT_MINUTES
            );
            closedCount++;
        }
        return closedCount;
    }

    private ResponseResult<?> evaluatePaymentRisk(TradeOrder order, Long buyerId, String paymentMethod) {
        User buyer = userService.getById(buyerId);
        if (buyer == null || Boolean.FALSE.equals(buyer.getEnabled())) {
            operationAuditLogService.log(
                    buyerId,
                    "SYSTEM",
                    "ORDER_RISK_REJECTED",
                    "TRADE_ORDER",
                    String.valueOf(order.getId()),
                    "reason=BUYER_DISABLED_OR_NOT_FOUND"
            );
            return ResponseResult.error("当前账户存在异常，暂时无法支付，请联系平台客服");
        }

        if (order.getCreateTime() != null &&
                order.getCreateTime().plusMinutes(PAYMENT_TIMEOUT_MINUTES).isBefore(LocalDateTime.now())) {
            operationAuditLogService.log(
                    buyerId,
                    "SYSTEM",
                    "ORDER_RISK_REJECTED",
                    "TRADE_ORDER",
                    String.valueOf(order.getId()),
                    "reason=ORDER_TIMEOUT"
            );
            return ResponseResult.error("订单已超时，请重新下单");
        }

        if (order.getOrderPrice() != null && order.getOrderPrice().compareTo(new BigDecimal("5000")) >= 0) {
            operationAuditLogService.log(
                    buyerId,
                    "SYSTEM",
                    "ORDER_RISK_REVIEW",
                    "TRADE_ORDER",
                    String.valueOf(order.getId()),
                    "reason=HIGH_AMOUNT, amount=" + order.getOrderPrice() + ", paymentMethod=" + paymentMethod
            );
            return ResponseResult.error(409, "订单触发风控审核，请稍后重试或联系人工客服");
        }

        operationAuditLogService.log(
                buyerId,
                "SYSTEM",
                "ORDER_RISK_PASS",
                "TRADE_ORDER",
                String.valueOf(order.getId()),
                "paymentMethod=" + paymentMethod
        );
        return null;
    }

    private void restoreGoodsStock(Long goodsId) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods != null) {
            goods.setStock(goods.getStock() + 1);
            if (goods.getStatus() == 3) {
                goods.setStatus(1);
            }
            goodsMapper.updateById(goods);
        }
    }
}