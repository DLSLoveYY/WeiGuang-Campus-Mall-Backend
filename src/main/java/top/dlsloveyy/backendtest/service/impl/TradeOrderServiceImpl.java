package top.dlsloveyy.backendtest.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.TradeOrder;
import top.dlsloveyy.backendtest.entity.User; // 🚀 引入 User 实体
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.TradeOrderMapper;
import top.dlsloveyy.backendtest.model.dto.OrderCreateDTO;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.model.vo.TradeOrderVO;
import top.dlsloveyy.backendtest.service.TradeOrderService;
import top.dlsloveyy.backendtest.service.UserService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.05");

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
            BigDecimal platformFee = orderPrice.multiply(PLATFORM_FEE_RATE).setScale(2, BigDecimal.ROUND_HALF_UP);
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
            order.setStatus(0); // 0: 待支付
            order.setCreateTime(LocalDateTime.now());

            tradeOrderMapper.insert(order);

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
        if (order.getStatus() != 0) {
            return ResponseResult.error("订单当前状态无法支付");
        }

        // 🚀 核心：根据支付方式执行不同逻辑
        if ("BALANCE".equals(paymentMethod)) {
            // 走平台余额扣款
            User buyer = userService.getById(buyerId);
            BigDecimal currentBalance = buyer.getBalance() != null ? buyer.getBalance() : BigDecimal.ZERO;

            // 判断余额是否足够
            if (currentBalance.compareTo(order.getOrderPrice()) < 0) {
                return ResponseResult.error("账户余额不足，请充值或使用微信/支付宝支付");
            }

            // 执行扣款
            buyer.setBalance(currentBalance.subtract(order.getOrderPrice()));
            userService.updateById(buyer);
        } else {
            // 走第三方模拟支付 (微信/支付宝)
            order.setPaymentNo("SIMULATE_" + paymentMethod + "_" + System.currentTimeMillis());
        }

        // 统一修改状态：0(待支付) -> 1(待发货)
        order.setStatus(1);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        this.updateById(order);

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
        if (order.getStatus() != 1) {
            return ResponseResult.error("订单当前状态无法发货");
        }

        order.setStatus(2);
        order.setDeliveryTime(LocalDateTime.now()); // 记录发货时间
        order.setUpdateTime(LocalDateTime.now());
        this.updateById(order);

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
        if (order.getStatus() != 2) {
            return ResponseResult.error("订单当前状态无法确认收货");
        }

        order.setStatus(3);
        LocalDateTime now = LocalDateTime.now();
        order.setUpdateTime(now);
        order.setFinishTime(now);
        this.updateById(order);

        try {
            userService.increaseBalance(order.getSellerId(), order.getSellerIncome());
        } catch (Exception e) {
            throw new RuntimeException("资金结算异常，确认收货失败: " + e.getMessage());
        }

        return ResponseResult.success("交易完成，钱款已汇入卖家账户！");
    }

    // ==================== 退款核心逻辑 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> applyRefund(Long orderId, Long buyerId) {
        TradeOrder order = this.getById(orderId);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            return ResponseResult.error("无权操作或订单不存在");
        }

        if (order.getStatus() == 1) {
            order.setStatus(5);
            order.setUpdateTime(LocalDateTime.now());
            this.updateById(order);

            restoreGoodsStock(order.getGoodsId());

            try {
                userService.increaseBalance(order.getBuyerId(), order.getOrderPrice());
            } catch (Exception e) {
                throw new RuntimeException("退款失败：资金退还异常 - " + e.getMessage());
            }

            return ResponseResult.success("退款成功，资金已原路返回");
        } else if (order.getStatus() == 2) {
            order.setStatus(6);
            order.setUpdateTime(LocalDateTime.now());
            this.updateById(order);
            return ResponseResult.success("退款申请已提交，等待卖家处理");
        } else {
            return ResponseResult.error("当前订单状态不支持退款");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> approveRefund(Long orderId, Long sellerId) {
        TradeOrder order = this.getById(orderId);
        if (order == null || !order.getSellerId().equals(sellerId)) {
            return ResponseResult.error("无权操作");
        }
        if (order.getStatus() != 6) {
            return ResponseResult.error("该订单未申请退款");
        }

        order.setStatus(5);
        order.setUpdateTime(LocalDateTime.now());
        this.updateById(order);
        restoreGoodsStock(order.getGoodsId());

        try {
            userService.increaseBalance(order.getBuyerId(), order.getOrderPrice());
        } catch (Exception e) {
            throw new RuntimeException("退款失败：资金退还异常 - " + e.getMessage());
        }

        return ResponseResult.success("已同意退款，资金已退回买家账户");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> rejectRefund(Long orderId, Long sellerId) {
        TradeOrder order = this.getById(orderId);
        if (order == null || !order.getSellerId().equals(sellerId)) {
            return ResponseResult.error("无权操作");
        }
        if (order.getStatus() != 6) {
            return ResponseResult.error("该订单未申请退款");
        }

        order.setStatus(2);
        order.setUpdateTime(LocalDateTime.now());
        this.updateById(order);

        return ResponseResult.success("已拒绝退款申请");
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