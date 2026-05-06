package top.dlsloveyy.backendtest.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.dlsloveyy.backendtest.entity.CustomerServiceCase;
import top.dlsloveyy.backendtest.entity.FreightTemplate;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.TradeDispute;
import top.dlsloveyy.backendtest.entity.TradeLogisticsTrace;
import top.dlsloveyy.backendtest.entity.TradeOrder;
import top.dlsloveyy.backendtest.entity.TradeShipment;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.entity.UserAddress;
import top.dlsloveyy.backendtest.mapper.FreightTemplateMapper;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.TradeOrderMapper;
import top.dlsloveyy.backendtest.mapper.TradeLogisticsTraceMapper;
import top.dlsloveyy.backendtest.mapper.TradeShipmentMapper;
import top.dlsloveyy.backendtest.mapper.UserAddressMapper;
import top.dlsloveyy.backendtest.model.dto.OrderCreateDTO;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.model.vo.TradeOrderVO;
import top.dlsloveyy.backendtest.service.AccountService;
import top.dlsloveyy.backendtest.service.CustomerServiceCaseService;
import top.dlsloveyy.backendtest.service.OperationAuditLogService;
import top.dlsloveyy.backendtest.service.TradeDisputeService;
import top.dlsloveyy.backendtest.service.TradeOrderService;
import top.dlsloveyy.backendtest.service.UserService;
import top.dlsloveyy.backendtest.service.UserNotificationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private UserAddressMapper userAddressMapper;

    @Autowired
    private FreightTemplateMapper freightTemplateMapper;

    @Autowired
    private TradeShipmentMapper tradeShipmentMapper;

    @Autowired
    private TradeLogisticsTraceMapper tradeLogisticsTraceMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private CustomerServiceCaseService customerServiceCaseService;

    @Autowired
    private OperationAuditLogService operationAuditLogService;

    @Autowired
    private TradeDisputeService tradeDisputeService;

    @Autowired
    private UserNotificationService userNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

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
        Boolean isLocked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", 30, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(isLocked)) {
            return ResponseResult.error("商品太火爆啦，正在被别人抢购中，请稍后再试！");
        }

        try {
            // C3 Fix: Use Redis lock (30s) + DB transaction for consistency
            // Combined approach: distributed lock + transactional DB operations
            Goods goods = goodsMapper.selectById(goodsId);
            if (goods == null) {
                return ResponseResult.error("商品不存在！");
            }
            if (goods.getStock() == null || goods.getStock() <= 0) {
                return ResponseResult.error("手慢了，该商品已售罄！");
            }
            if (goods.getStatus() != 1) {
                return ResponseResult.error("该商品目前不可购买！");
            }
            if (goods.getSellerId().equals(currentUserId)) {
                return ResponseResult.error("不能购买自己发布的商品哦！");
            }

            String deliveryMethod;
            try {
                deliveryMethod = normalizeDeliveryMethod(dto.getDeliveryMethod());
            } catch (IllegalArgumentException e) {
                return ResponseResult.error(e.getMessage());
            }
            java.util.Set<String> allowedMethods = parseAllowedMethods(goods.getDeliveryMethods(), goods.getDeliveryMethod());
            if (!allowedMethods.contains(deliveryMethod)) {
                return ResponseResult.error("该商品不支持你选择的交易方式");
            }

            Long addressId = null;
            String meetupAddress = null;
            String meetupPhone = null;
            String deliveryAddress = null;
            if ("邮寄".equals(deliveryMethod)) {
                if (dto.getAddressId() == null) {
                    return ResponseResult.error("邮寄订单必须选择收货地址");
                }
                UserAddress address = userAddressMapper.selectById(dto.getAddressId());
                if (address == null || !currentUserId.equals(address.getUserId())) {
                    return ResponseResult.error("收货地址不存在或无权限");
                }
                addressId = address.getId();
                deliveryAddress = buildAddressSnapshot(address);
            } else {
                meetupAddress = dto.getMeetupAddress() == null ? "" : dto.getMeetupAddress().trim();
                meetupPhone = dto.getMeetupPhone() == null ? "" : dto.getMeetupPhone().trim();
                if (meetupAddress.isEmpty()) {
                    return ResponseResult.error("校园面交必须填写详细交易地址");
                }
                if (!meetupPhone.matches("^1\\d{10}$")) {
                    return ResponseResult.error("校园面交手机号格式不正确");
                }
                deliveryAddress = meetupAddress;
            }

            BigDecimal goodsPrice = goods.getPrice();
            if (goodsPrice == null || goodsPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseResult.error("商品价格异常，暂时无法下单");
            }
            BigDecimal freightFee = calculateFreightFee(goods.getSellerId(), deliveryMethod, goodsPrice);
            BigDecimal payAmount = goodsPrice.add(freightFee);
            BigDecimal platformFee = goodsPrice.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal sellerIncome = goodsPrice.subtract(platformFee).add(freightFee);

            TradeOrder order = new TradeOrder();
            String orderNo = "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
            order.setOrderNo(orderNo);
            order.setBuyerId(currentUserId);
            order.setSellerId(goods.getSellerId());
            order.setGoodsId(goodsId);
            order.setOrderPrice(payAmount);
            order.setFreightFee(freightFee);
            order.setPlatformFee(platformFee);
            order.setSellerIncome(sellerIncome);
            order.setDeliveryMethod(deliveryMethod);
            order.setDeliveryAddress(deliveryAddress);
            order.setAddressId(addressId);
            order.setMeetupAddress(meetupAddress);
            order.setMeetupPhone(meetupPhone);
            order.setStatus(PENDING_PAYMENT);
            order.setCreateTime(LocalDateTime.now());

            tradeOrderMapper.insert(order);

            operationAuditLogService.log(
                    currentUserId,
                    "USER",
                    "ORDER_CREATED",
                    "TRADE_ORDER",
                    String.valueOf(order.getId()),
                    "goodsId=" + goodsId + ", status=" + PENDING_PAYMENT + ", freightFee=" + freightFee
            );

                userNotificationService.notifyUser(
                    order.getSellerId(),
                    "ORDER_CREATED",
                    "您有一笔新订单",
                    "商品《" + goods.getTitle() + "》已被下单，当前等待买家支付。",
                    "TRADE_ORDER",
                    order.getId());

            int newStock = (goods.getStock() == null ? 0 : goods.getStock() - 1);
            goods.setStock(newStock);
            if (newStock <= 0) {
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

        BigDecimal escrowAmount = resolveEscrowAmount(order);
        if (escrowAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("订单金额异常，无法完成支付");
        }

        if ("BALANCE".equals(normalizedMethod)) {
            accountService.debit(
                    buyerId,
                    order.getOrderPrice(),
                    "ORDER_PAY",
                    String.valueOf(orderId),
                    "ORDER_PAY:OUT:" + orderId + ":" + buyerId,
                    "余额支付订单"
            );
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

        userNotificationService.notifyUser(
            order.getSellerId(),
            "ORDER_PAID",
            "买家已完成支付",
            "订单 " + order.getOrderNo() + " 已支付，请尽快发货。",
            "TRADE_ORDER",
            orderId);
        userNotificationService.notifyUser(
            order.getBuyerId(),
            "ORDER_PAID",
            "支付成功",
            "您的订单已支付成功，等待卖家发货。",
            "TRADE_ORDER",
            orderId);

        accountService.freeze(
            order.getSellerId(),
            escrowAmount,
            "ORDER_SETTLE_FROZEN",
            String.valueOf(orderId),
            "ORDER_SETTLE_FROZEN:" + orderId + ":" + order.getSellerId(),
            "订单支付成功，冻结卖家应收金额"
        );

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
    public ResponseResult<?> shipOrder(Long orderId, Long sellerId, String carrierCode, String trackingNo, String buyerPhoneSuffix) {
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

        String normalizedMethod = normalizeDeliveryMethod(order.getDeliveryMethod());
        if ("校园面交".equals(normalizedMethod)) {
            String suffix = buyerPhoneSuffix == null ? "" : buyerPhoneSuffix.trim();
            if (!suffix.matches("^\\d{4}$")) {
                return ResponseResult.error("校园面交确认时需输入买家手机号后4位");
            }
            String meetupPhone = order.getMeetupPhone() == null ? "" : order.getMeetupPhone().trim();
            if (meetupPhone.length() < 4 || !meetupPhone.endsWith(suffix)) {
                return ResponseResult.error("手机号后4位不匹配，无法确认交货");
            }
            LocalDateTime now = LocalDateTime.now();
            order.setSellerConfirmPhoneSuffix(suffix);
            order.setHandoffConfirmTime(now);
            order.setStatus(SHIPPED_PENDING_RECEIPT);
            order.setDeliveryTime(now);
            order.setUpdateTime(now);
            this.updateById(order);

            operationAuditLogService.log(
                    sellerId,
                    "USER",
                    "ORDER_HANDOFF_CONFIRMED",
                    "TRADE_ORDER",
                    String.valueOf(orderId),
                    "status=" + SHIPPED_PENDING_RECEIPT + ", meetupAddress=" + (order.getMeetupAddress() == null ? "" : order.getMeetupAddress())
            );
                userNotificationService.notifyUser(
                    order.getBuyerId(),
                    "ORDER_SHIPPED",
                    "卖家已确认面交",
                    "您的订单已完成面交确认。",
                    "TRADE_ORDER",
                    orderId);
            return ResponseResult.success("面交确认成功");
        }

        String normalizedCarrier = carrierCode == null ? "" : carrierCode.trim();
        String normalizedTrackingNo = trackingNo == null ? "" : trackingNo.trim();
        if (normalizedCarrier.isEmpty() || normalizedTrackingNo.isEmpty()) {
            return ResponseResult.error("邮寄发货必须填写物流公司和运单号");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setCarrierCode(normalizedCarrier);
        order.setTrackingNo(normalizedTrackingNo);
        order.setStatus(SHIPPED_PENDING_RECEIPT);
        order.setDeliveryTime(now);
        order.setUpdateTime(now);
        this.updateById(order);

        TradeShipment shipment = tradeShipmentMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TradeShipment>()
                .eq(TradeShipment::getOrderId, orderId)
                .last("limit 1"));
        if (shipment == null) {
            shipment = new TradeShipment();
            shipment.setOrderId(orderId);
            shipment.setSellerId(order.getSellerId());
            shipment.setBuyerId(order.getBuyerId());
            shipment.setCreateTime(now);
        }
        shipment.setCarrierCode(normalizedCarrier);
        shipment.setTrackingNo(normalizedTrackingNo);
        shipment.setStatus(1);
        shipment.setShippedTime(now);
        shipment.setUpdateTime(now);
        if (shipment.getId() == null) {
            tradeShipmentMapper.insert(shipment);
        } else {
            tradeShipmentMapper.updateById(shipment);
        }

        TradeLogisticsTrace trace = new TradeLogisticsTrace();
        trace.setShipmentId(shipment.getId());
        trace.setTraceDesc("卖家已发货");
        trace.setTraceLocation("平台仓/卖家发货地");
        trace.setTraceTime(now);
        trace.setCreateTime(now);
        tradeLogisticsTraceMapper.insert(trace);

        userNotificationService.notifyUser(
            order.getBuyerId(),
            "ORDER_SHIPPED",
            "卖家已发货",
            "您的订单已发货，请注意查看物流信息。",
            "TRADE_ORDER",
            orderId);

        operationAuditLogService.log(
                sellerId,
                "USER",
                "ORDER_SHIPPED",
                "TRADE_ORDER",
                String.valueOf(orderId),
                "status=" + SHIPPED_PENDING_RECEIPT + ", carrier=" + normalizedCarrier + ", trackingNo=" + normalizedTrackingNo
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
        
        // 🔧 修复 Bug #9：检查是否有部分退款
        if (order.getRefundStage() != null && order.getRefundStage() >= 6 && 
            order.getRefundApprovedAmount() != null && 
            order.getRefundApprovedAmount().compareTo(order.getOrderPrice()) < 0) {
            return ResponseResult.error("订单存在部分退款，不能确认收货。请联系卖家处理");
        }

        order.setStatus(COMPLETED);
        LocalDateTime now = LocalDateTime.now();
        order.setUpdateTime(now);
        order.setFinishTime(now);
        this.updateById(order);

        BigDecimal escrowAmount = resolveEscrowAmount(order);
        try {
            // 🔧 修复 Bug #6：确认收货时解冻卖家冻结金额
            accountService.unfreeze(
                    order.getSellerId(),
                escrowAmount,
                    "ORDER_SETTLE_UNFROZEN",
                    String.valueOf(orderId),
                    "ORDER_SETTLE_UNFROZEN:" + orderId + ":" + order.getSellerId(),
                    "买家确认收货，解冻卖家金额"
            );
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

        userNotificationService.notifyUser(
            order.getSellerId(),
            "ORDER_COMPLETED",
            "买家已确认收货",
            "订单已完成，资金即将结算给卖家。",
            "TRADE_ORDER",
            orderId);
        userNotificationService.notifyUser(
            order.getBuyerId(),
            "ORDER_COMPLETED",
            "交易完成",
            "感谢您的交易，欢迎对卖家进行评价。",
            "TRADE_ORDER",
            orderId);

        return ResponseResult.success("交易完成，钱款已汇入卖家账户！");
    }

    // ==================== 退款核心逻辑 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> applyRefund(Long orderId,
                                         Long buyerId,
                                         Integer refundType,
                                         BigDecimal requestedAmount,
                                         String reason,
                                         String reasonCode,
                                         String reasonDetail,
                                         String buyerEvidence) {
        TradeOrder order = this.getById(orderId);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            return ResponseResult.error("无权操作或订单不存在");
        }
        
        // 🔧 修复 Bug #3：禁止重复申请退款
        if (order.getRefundStage() != null && order.getRefundStage() > 0) {
            return ResponseResult.error("订单已有退款/退货记录，不能重复申请");
        }
        
        if (order.getStatus() != PAID_PENDING_SHIPMENT && order.getStatus() != SHIPPED_PENDING_RECEIPT) {
            return ResponseResult.error("当前订单状态不支持退款");
        }

        int normalizedRefundType = refundType == null ? 1 : refundType;
        if (normalizedRefundType < 1 || normalizedRefundType > 3) {
            return ResponseResult.error("不支持的退款类型");
        }

        BigDecimal normalizedRequestedAmount = requestedAmount == null ? order.getOrderPrice() : requestedAmount;
        if (normalizedRequestedAmount.compareTo(BigDecimal.ZERO) <= 0 ||
                normalizedRequestedAmount.compareTo(order.getOrderPrice()) > 0) {
            return ResponseResult.error("退款金额非法");
        }

        if ("校园面交".equals(normalizeDeliveryMethod(order.getDeliveryMethod())) && normalizedRefundType == 2) {
            return ResponseResult.error("校园面交订单不支持退货退款，请选择仅退款");
        }

        if (order.getStatus() == PAID_PENDING_SHIPMENT && normalizedRefundType == 2) {
            return ResponseResult.error("当前订单尚未发货，不支持退货退款");
        }

        LocalDateTime now = LocalDateTime.now();
        String normalizedReasonCode = (reasonCode == null || reasonCode.isBlank()) ? "OTHER" : reasonCode.trim();
        String normalizedReasonDetail = reasonDetail == null ? "" : reasonDetail.trim();
        String finalReason = (reason == null || reason.isBlank())
            ? (normalizedReasonDetail.isBlank() ? "买家申请退款" : normalizedReasonDetail)
            : reason.trim();

        order.setRefundType(normalizedRefundType);
        order.setRefundRequestedAmount(normalizedRequestedAmount);
        order.setRefundApprovedAmount(null);
        order.setRefundReason(finalReason);
        order.setRefundReasonCode(normalizedReasonCode);
        order.setRefundApplyPacket(buildRefundApplyPacket(order, normalizedRefundType, normalizedRequestedAmount,
            normalizedReasonCode, normalizedReasonDetail, buyerEvidence, now));
        order.setRefundStage(1);
        order.setUpdateTime(now);

        if (order.getStatus() == PAID_PENDING_SHIPMENT && normalizedRefundType == 1) {
            order.setRefundApprovedAmount(normalizedRequestedAmount);
            order.setRefundStage(6);
            order.setStatus(REFUNDED);
            this.updateById(order);

            restoreGoodsStock(order.getGoodsId());
            executeRefund(order, normalizedRequestedAmount, "ORDER_REFUND_DIRECT", "发货前仅退款");

            userNotificationService.notifyUser(
                order.getSellerId(),
                "REFUND_APPLIED",
                "买家申请退款",
                "订单已进入退款处理，请尽快查看。",
                "TRADE_ORDER",
                orderId);
            userNotificationService.notifyUser(
                order.getBuyerId(),
                "REFUND_DIRECT_DONE",
                "退款成功",
                "您的退款已处理完成，金额将原路返回。",
                "TRADE_ORDER",
                orderId);

            operationAuditLogService.log(
                    buyerId,
                    "USER",
                    "ORDER_REFUNDED_DIRECT",
                    "TRADE_ORDER",
                    String.valueOf(orderId),
                    "status=" + REFUNDED + ", amount=" + normalizedRequestedAmount
            );
            return ResponseResult.success("退款成功，资金已原路返回");
        }

        order.setStatus(REFUND_REQUESTED);
        order.setRefundStage(2);
        this.updateById(order);

        userNotificationService.notifyUser(
            order.getSellerId(),
            "REFUND_APPLIED",
            "买家申请退款",
            "订单已提交退款申请，请及时处理。",
            "TRADE_ORDER",
            orderId);

        operationAuditLogService.log(
                buyerId,
                "USER",
                "ORDER_REFUND_APPLIED",
                "TRADE_ORDER",
                String.valueOf(orderId),
                "refundType=" + normalizedRefundType + ", requestedAmount=" + normalizedRequestedAmount
        );

        return ResponseResult.success("退款申请已提交，等待卖家处理");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> approveRefund(Long orderId, Long sellerId, BigDecimal approvedAmount) {
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

        BigDecimal finalApprovedAmount = approvedAmount == null
                ? (order.getRefundRequestedAmount() == null ? order.getOrderPrice() : order.getRefundRequestedAmount())
                : approvedAmount;
        if (finalApprovedAmount.compareTo(BigDecimal.ZERO) <= 0 ||
                finalApprovedAmount.compareTo(order.getOrderPrice()) > 0) {
            return ResponseResult.error("退款金额非法");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setRefundApprovedAmount(finalApprovedAmount);
        order.setUpdateTime(now);

        Integer refundType = order.getRefundType() == null ? 1 : order.getRefundType();
        if (refundType == 2) {
            // 🔧 修复 Bug #4：退货退款时正确改变订单状态
            order.setRefundStage(3);
            order.setStatus(REFUND_REQUESTED);  // 保持状态为退款申请中，等待买家回寄
            this.updateById(order);

            userNotificationService.notifyUser(
                order.getBuyerId(),
                "REFUND_APPROVED",
                "卖家同意退款",
                "卖家已同意退货退款，请提交退货物流。",
                "TRADE_ORDER",
                orderId);
            operationAuditLogService.log(
                    sellerId,
                    "USER",
                    "ORDER_REFUND_APPROVED_WAIT_RETURN",
                    "TRADE_ORDER",
                    String.valueOf(orderId),
                    "approvedAmount=" + finalApprovedAmount
            );
            return ResponseResult.success("已同意退货退款，等待买家提交退货物流单号");
        }

        boolean fullRefund = finalApprovedAmount.compareTo(order.getOrderPrice()) == 0;
        order.setRefundStage(6);
        order.setStatus(fullRefund ? REFUNDED : SHIPPED_PENDING_RECEIPT);
        this.updateById(order);

        if (order.getStatus() == REFUNDED && order.getDeliveryTime() == null) {
            restoreGoodsStock(order.getGoodsId());
        }

        executeRefund(order, finalApprovedAmount, "ORDER_REFUND_APPROVED", "卖家同意退款");

        userNotificationService.notifyUser(
            order.getBuyerId(),
            "REFUND_APPROVED",
            "卖家同意退款",
            fullRefund ? "您的退款已完成。" : "您的退款已部分处理完成。",
            "TRADE_ORDER",
            orderId);

        operationAuditLogService.log(
                sellerId,
                "USER",
                "ORDER_REFUND_APPROVED",
                "TRADE_ORDER",
                String.valueOf(orderId),
                "approvedAmount=" + finalApprovedAmount + ", fullRefund=" + fullRefund
        );

        return ResponseResult.success(fullRefund ? "已同意退款，资金已退回买家账户" : "已同意部分退款，订单继续履约");
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

        order.setRefundStage(7);
        order.setStatus(SHIPPED_PENDING_RECEIPT);
        order.setUpdateTime(LocalDateTime.now());
        this.updateById(order);

        userNotificationService.notifyUser(
            order.getBuyerId(),
            "REFUND_REJECTED",
            "卖家拒绝退款",
            "如仍有争议，您可以发起客服介入。",
            "TRADE_ORDER",
            orderId);

        // 🔧 修复 Bug #2：拒绝退款时恢复库存
        if (order.getRefundType() != null && order.getRefundType() == 1 && order.getStatus() == PAID_PENDING_SHIPMENT) {
            restoreGoodsStock(order.getGoodsId());
        }

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
    public ResponseResult<?> submitReturnTracking(Long orderId, Long buyerId, String returnTrackingNo) {
        TradeOrder order = this.getById(orderId);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            return ResponseResult.error("无权操作或订单不存在");
        }
        if (order.getStatus() != REFUND_REQUESTED || !Integer.valueOf(2).equals(order.getRefundType()) ||
                !Integer.valueOf(3).equals(order.getRefundStage())) {
            return ResponseResult.error("当前订单不在可提交退货物流状态");
        }
        if (returnTrackingNo == null || returnTrackingNo.trim().isEmpty()) {
            return ResponseResult.error("退货物流单号不能为空");
        }

        order.setReturnTrackingNo(returnTrackingNo.trim());
        order.setRefundStage(4);
        order.setUpdateTime(LocalDateTime.now());
        this.updateById(order);

        userNotificationService.notifyUser(
            order.getSellerId(),
            "RETURN_TRACKING_SUBMITTED",
            "买家已提交退货物流",
            "请及时关注退货物流并准备收货。",
            "TRADE_ORDER",
            orderId);

        operationAuditLogService.log(
                buyerId,
                "USER",
                "ORDER_RETURN_TRACKING_SUBMITTED",
                "TRADE_ORDER",
                String.valueOf(orderId),
                "returnTrackingNo=" + returnTrackingNo.trim()
        );

        return ResponseResult.success("退货物流信息已提交，等待卖家确认收货");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> confirmReturnReceived(Long orderId, Long sellerId, BigDecimal approvedAmount) {
        TradeOrder order = this.getById(orderId);
        if (order == null || !order.getSellerId().equals(sellerId)) {
            return ResponseResult.error("无权操作");
        }
        if (order.getStatus() != REFUND_REQUESTED || !Integer.valueOf(2).equals(order.getRefundType()) ||
                !Integer.valueOf(4).equals(order.getRefundStage())) {
            return ResponseResult.error("当前订单不在可确认退货状态");
        }

        BigDecimal finalApprovedAmount = approvedAmount == null
                ? (order.getRefundRequestedAmount() == null ? order.getOrderPrice() : order.getRefundRequestedAmount())
                : approvedAmount;
        if (finalApprovedAmount.compareTo(BigDecimal.ZERO) <= 0 ||
                finalApprovedAmount.compareTo(order.getOrderPrice()) > 0) {
            return ResponseResult.error("退款金额非法");
        }

        boolean fullRefund = finalApprovedAmount.compareTo(order.getOrderPrice()) == 0;
        order.setRefundApprovedAmount(finalApprovedAmount);
        order.setRefundStage(6);
        order.setStatus(fullRefund ? REFUNDED : SHIPPED_PENDING_RECEIPT);
        order.setUpdateTime(LocalDateTime.now());
        this.updateById(order);

        // 🔧 修复 Bug #1：完整退货退款时恢复库存
        if (fullRefund) {
            restoreGoodsStock(order.getGoodsId());
        }

        executeRefund(order, finalApprovedAmount, "ORDER_RETURN_REFUND_CONFIRMED", "卖家确认收货后退款");

        userNotificationService.notifyUser(
            order.getBuyerId(),
            "RETURN_CONFIRMED",
            "卖家已确认退货",
            fullRefund ? "退货退款已完成。" : "退货退款已部分完成。",
            "TRADE_ORDER",
            orderId);

        operationAuditLogService.log(
                sellerId,
                "USER",
                "ORDER_RETURN_REFUND_CONFIRMED",
                "TRADE_ORDER",
                String.valueOf(orderId),
                "approvedAmount=" + finalApprovedAmount + ", fullRefund=" + fullRefund
        );

        return ResponseResult.success(fullRefund ? "退货退款完成" : "部分退款完成，订单继续履约");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> cancelOrderByBuyer(Long orderId, Long buyerId, String reason) {
        TradeOrder order = this.getById(orderId);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            return ResponseResult.error("无权操作或订单不存在");
        }
        if (order.getStatus() != PENDING_PAYMENT) {
            return ResponseResult.error("仅待支付订单可取消");
        }

        closeOrderWithReason(
                order,
                "BUYER_CANCEL",
                reason == null || reason.trim().isEmpty() ? "买家主动取消订单" : reason.trim(),
                "BUYER"
        );

        userNotificationService.notifyUser(
            order.getSellerId(),
            "ORDER_CANCELED",
            "买家取消订单",
            "订单已被买家取消。",
            "TRADE_ORDER",
            orderId);

        operationAuditLogService.log(
                buyerId,
                "USER",
                "ORDER_CANCELED_BY_BUYER",
                "TRADE_ORDER",
                String.valueOf(orderId),
                "reason=" + (reason == null ? "" : reason)
        );

        return ResponseResult.success("订单已取消");
    }

    @Override
    public ResponseResult<?> getLogisticsTrace(Long orderId, Long userId) {
        TradeOrder order = this.getById(orderId);
        if (order == null) {
            return ResponseResult.error("订单不存在");
        }
        if (!userId.equals(order.getBuyerId()) && !userId.equals(order.getSellerId())) {
            return ResponseResult.error("无权查看该订单物流");
        }

        TradeShipment shipment = tradeShipmentMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TradeShipment>()
                .eq(TradeShipment::getOrderId, orderId)
                .last("limit 1"));
        if (shipment == null) {
            return ResponseResult.success("暂无物流信息", new HashMap<>());
        }

        List<TradeLogisticsTrace> traces = tradeLogisticsTraceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TradeLogisticsTrace>()
                .eq(TradeLogisticsTrace::getShipmentId, shipment.getId())
                .orderByDesc(TradeLogisticsTrace::getTraceTime));

        Map<String, Object> data = new HashMap<>();
        data.put("carrierCode", shipment.getCarrierCode());
        data.put("trackingNo", shipment.getTrackingNo());
        data.put("shipmentStatus", shipment.getStatus());
        data.put("traces", traces);
        return ResponseResult.success(data);
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
            boolean updated = closeOrderWithReason(
                    order,
                    "PAY_TIMEOUT_AUTO_CLOSE",
                    "订单超时未支付，系统自动关闭",
                    "SYSTEM"
            );
            if (!updated) {
                continue;
            }

            restoreGoodsStock(order.getGoodsId());
                userNotificationService.notifyUser(
                    order.getBuyerId(),
                    "ORDER_TIMEOUT_CLOSED",
                    "订单已超时关闭",
                    "订单因长时间未支付已自动关闭，库存已释放。",
                    "TRADE_ORDER",
                    order.getId());
                userNotificationService.notifyUser(
                    order.getSellerId(),
                    "ORDER_TIMEOUT_CLOSED",
                    "订单已超时关闭",
                    "有一笔待支付订单因超时被系统关闭。",
                    "TRADE_ORDER",
                    order.getId());
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

    private void executeRefund(TradeOrder order,
                               BigDecimal amount,
                               String bizType,
                               String remark) {
        try {
            // 🔧 修复 Bug #7：退款时解冻卖家的冻结金额
            BigDecimal unfreezeAmount = resolveEscrowAmount(order);
            if (unfreezeAmount != null && unfreezeAmount.compareTo(BigDecimal.ZERO) > 0) {
                accountService.unfreeze(
                        order.getSellerId(),
                        unfreezeAmount,
                        "ORDER_REFUND_UNFREEZE",
                        String.valueOf(order.getId()),
                        "ORDER_REFUND_UNFREEZE:" + order.getId() + ":" + order.getSellerId(),
                        "退款时解冻卖家冻结金额"
                );
            }
            
            // 退款给买家
            accountService.credit(
                    order.getBuyerId(),
                    amount,
                    bizType,
                    String.valueOf(order.getId()),
                    bizType + ":IN:" + order.getId() + ":" + order.getBuyerId() + ":" + amount,
                    remark
            );
        } catch (Exception e) {
            throw new RuntimeException("退款失败：资金退还异常 - " + e.getMessage());
        }
    }

    private BigDecimal resolveEscrowAmount(TradeOrder order) {
        if (order.getSellerIncome() != null && order.getSellerIncome().compareTo(BigDecimal.ZERO) > 0) {
            return order.getSellerIncome();
        }
        if (order.getOrderPrice() != null && order.getOrderPrice().compareTo(BigDecimal.ZERO) > 0) {
            return order.getOrderPrice();
        }
        return BigDecimal.ZERO;
    }

    private String buildRefundApplyPacket(TradeOrder order,
                                          Integer refundType,
                                          BigDecimal requestedAmount,
                                          String reasonCode,
                                          String reasonDetail,
                                          String buyerEvidence,
                                          LocalDateTime applyTime) {
        Map<String, Object> packet = new HashMap<>();
        packet.put("orderId", order.getId());
        packet.put("orderNo", order.getOrderNo());
        packet.put("buyerId", order.getBuyerId());
        packet.put("sellerId", order.getSellerId());
        packet.put("goodsId", order.getGoodsId());
        packet.put("refundType", refundType);
        packet.put("requestedAmount", requestedAmount);
        packet.put("reasonCode", reasonCode);
        packet.put("reasonDetail", reasonDetail);
        packet.put("buyerEvidence", buyerEvidence);
        packet.put("deliveryMethod", order.getDeliveryMethod());
        packet.put("deliveryAddress", order.getDeliveryAddress());
        packet.put("applyTime", applyTime == null ? LocalDateTime.now() : applyTime);
        try {
            return objectMapper.writeValueAsString(packet);
        } catch (JsonProcessingException e) {
            return "{\"orderId\":" + order.getId() + ",\"orderNo\":\"" + order.getOrderNo() + "\",\"reasonCode\":\"" + reasonCode + "\"}";
        }
    }

    private boolean closeOrderWithReason(TradeOrder order,
                                         String reasonCode,
                                         String reasonDesc,
                                         String source) {
        LocalDateTime now = LocalDateTime.now();
        return this.lambdaUpdate()
                .eq(TradeOrder::getId, order.getId())
                .eq(TradeOrder::getStatus, PENDING_PAYMENT)
                .set(TradeOrder::getStatus, CLOSED)
                .set(TradeOrder::getCancelReasonCode, reasonCode)
                .set(TradeOrder::getCancelReasonDesc, reasonDesc)
                .set(TradeOrder::getCancelSource, source)
                .set(TradeOrder::getUpdateTime, now)
                .set(TradeOrder::getCancelTime, now)
                .update();
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

    private java.util.Set<String> parseAllowedMethods(String deliveryMethodsCsv, String legacyMethod) {
        java.util.LinkedHashSet<String> methods = new java.util.LinkedHashSet<>();
        collectMethod(methods, deliveryMethodsCsv);
        collectMethod(methods, legacyMethod);
        if (methods.isEmpty()) {
            methods.add("校园面交");
        }
        return methods;
    }

    private String normalizeDeliveryMethod(String method) {
        String normalized = method == null ? "" : method.trim();
        if ("自提".equals(normalized) || "买家自提".equals(normalized)) {
            normalized = "校园面交";
        }
        if (!"校园面交".equals(normalized) && !"邮寄".equals(normalized)) {
            throw new IllegalArgumentException("交易方式仅支持校园面交或邮寄");
        }
        return normalized;
    }

    private void collectMethod(java.util.Set<String> methods, String source) {
        if (source == null || source.isBlank()) {
            return;
        }
        for (String item : source.split(",")) {
            String normalized = normalizeDeliveryMethod(item);
            methods.add(normalized);
        }
    }

    private String buildAddressSnapshot(UserAddress address) {
        if (address == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (address.getProvince() != null) sb.append(address.getProvince());
        if (address.getCity() != null) sb.append(address.getCity());
        if (address.getDistrict() != null) sb.append(address.getDistrict());
        if (address.getDetail() != null) sb.append(address.getDetail());
        sb.append("，收件人:");
        if (address.getReceiverName() != null) {
            sb.append(address.getReceiverName());
        }
        sb.append("，电话:");
        if (address.getReceiverPhone() != null) {
            sb.append(address.getReceiverPhone());
        }
        return sb.toString();
    }

    private BigDecimal calculateFreightFee(Long sellerId, String deliveryMethod, BigDecimal goodsAmount) {
        if (!"邮寄".equals(deliveryMethod)) {
            return BigDecimal.ZERO;
        }

        FreightTemplate template = freightTemplateMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FreightTemplate>()
                .eq(FreightTemplate::getSellerId, sellerId)
                .eq(FreightTemplate::getEnabled, 1)
                .orderByDesc(FreightTemplate::getUpdateTime)
                .last("limit 1"));

        if (template == null) {
            return new BigDecimal("8.00");
        }

        BigDecimal threshold = template.getFreeShippingThreshold();
        if (threshold != null && goodsAmount.compareTo(threshold) >= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseFee = template.getBaseFee() == null ? BigDecimal.ZERO : template.getBaseFee();
        BigDecimal extraFee = template.getExtraFeePerItem() == null ? BigDecimal.ZERO : template.getExtraFeePerItem();
        return baseFee.add(extraFee).setScale(2, RoundingMode.HALF_UP);
    }

    private void restoreGoodsStock(Long goodsId) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods != null) {
            int currentStock = goods.getStock() == null ? 0 : goods.getStock();
            goods.setStock(currentStock + 1);
            if (goods.getStatus() == 3) {
                goods.setStatus(1);
            }
            goodsMapper.updateById(goods);
        }
    }
}