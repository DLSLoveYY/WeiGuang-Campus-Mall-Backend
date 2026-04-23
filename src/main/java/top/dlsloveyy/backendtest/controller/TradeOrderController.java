package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.TradeOrder;
import top.dlsloveyy.backendtest.model.dto.OrderCreateDTO;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.service.TradeOrderService;
import top.dlsloveyy.backendtest.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;

import static top.dlsloveyy.backendtest.constant.OrderStatus.PAID_PENDING_SHIPMENT;
import static top.dlsloveyy.backendtest.constant.OrderStatus.REFUND_REQUESTED;
import static top.dlsloveyy.backendtest.constant.OrderStatus.SHIPPED_PENDING_RECEIPT;

@RestController
@RequestMapping("/api/order")
public class TradeOrderController {

    @Autowired
    private TradeOrderService tradeOrderService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 辅助方法：从 Request 请求头中提取解析 UserID
     */
    private Long getUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtil.getUserIdFromToken(token.substring(7));
        }
        return null;
    }

    // ==================== 1. 订单创建与查询 ====================

    @PostMapping("/create")
    public ResponseResult<?> createOrder(@RequestBody OrderCreateDTO dto, HttpServletRequest request) {
        Long currentUserId = getUserId(request);
        if (currentUserId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return tradeOrderService.createOrder(dto, currentUserId);
    }

    @GetMapping("/my/purchases")
    public ResponseResult<?> getMyPurchases(HttpServletRequest request) {
        Long buyerId = getUserId(request);
        if (buyerId == null) return ResponseResult.error(401, "请先登录");
        return tradeOrderService.getMyPurchases(buyerId);
    }

    @GetMapping("/my/sales")
    public ResponseResult<?> getMySales(HttpServletRequest request) {
        Long sellerId = getUserId(request);
        if (sellerId == null) return ResponseResult.error(401, "请先登录");
        return tradeOrderService.getMySales(sellerId);
    }

    // ==================== 2. 订单状态流转逻辑 ====================

    /**
     * 去支付 (买家操作)
     * 🚀 新增 method 参数，默认走 WECHAT
     */
    @PutMapping("/pay/{id}")
    public ResponseResult<?> payOrder(
            @PathVariable Long id,
            @RequestParam(defaultValue = "WECHAT") String method, // 接收前端传来的支付方式
            HttpServletRequest request) {

        Long buyerId = getUserId(request);
        if (buyerId == null) return ResponseResult.error(401, "请先登录");

        return tradeOrderService.payOrder(id, buyerId, method);
    }

    /**
     * 确认发货 (卖家操作)
     */
    @PutMapping("/ship/{id}")
    public ResponseResult<?> shipOrder(@PathVariable Long id,
                                       @RequestBody(required = false) java.util.Map<String, String> payload,
                                       HttpServletRequest request) {
        Long sellerId = getUserId(request);
        if (sellerId == null) return ResponseResult.error(401, "请先登录");
        String carrierCode = payload == null ? null : payload.get("carrierCode");
        String trackingNo = payload == null ? null : payload.get("trackingNo");
        String buyerPhoneSuffix = payload == null ? null : payload.get("buyerPhoneSuffix");
        return tradeOrderService.shipOrder(id, sellerId, carrierCode, trackingNo, buyerPhoneSuffix);
    }

    /**
     * 确认收货 (买家操作)
     */
    @PutMapping("/receive/{id}")
    public ResponseResult<?> receiveOrder(@PathVariable Long id, HttpServletRequest request) {
        Long buyerId = getUserId(request);
        if (buyerId == null) return ResponseResult.error(401, "请先登录");
        return tradeOrderService.receiveOrder(id, buyerId);
    }

    // ==================== 3. 逆向流程：退款逻辑 ====================

    /**
     * 申请退款 (买家操作)
     */
    @PutMapping("/refund/apply/{id}")
    public ResponseResult<?> applyRefund(@PathVariable Long id,
                                         @RequestBody(required = false) java.util.Map<String, Object> payload,
                                         HttpServletRequest request) {
        Long buyerId = getUserId(request);
        if (buyerId == null) return ResponseResult.error(401, "请先登录");
        String reason = payload == null ? null : (String) payload.get("reason");
        String reasonCode = payload == null ? null : (String) payload.get("reasonCode");
        String reasonDetail = payload == null ? null : (String) payload.get("reasonDetail");
        String buyerEvidence = payload == null ? null : (String) payload.get("buyerEvidence");

        Integer refundType = 1;
        if (payload != null && payload.get("refundType") != null) {
            Object typeObj = payload.get("refundType");
            if (typeObj instanceof Number number) {
                refundType = number.intValue();
            } else {
                refundType = Integer.parseInt(String.valueOf(typeObj));
            }
        }

        java.math.BigDecimal requestedAmount = null;
        if (payload != null && payload.get("requestedAmount") != null) {
            Object amountObj = payload.get("requestedAmount");
            if (amountObj instanceof Number number) {
                requestedAmount = java.math.BigDecimal.valueOf(number.doubleValue());
            } else {
                requestedAmount = new java.math.BigDecimal(String.valueOf(amountObj));
            }
        }
        return tradeOrderService.applyRefund(id, buyerId, refundType, requestedAmount, reason, reasonCode, reasonDetail, buyerEvidence);
    }


    /**
     * 同意退款 (卖家操作)
     */
    @PutMapping("/refund/approve/{id}")
    public ResponseResult<?> approveRefund(@PathVariable Long id,
                                           @RequestBody(required = false) java.util.Map<String, Object> payload,
                                           HttpServletRequest request) {
        Long sellerId = getUserId(request);
        if (sellerId == null) return ResponseResult.error(401, "请先登录");
        java.math.BigDecimal approvedAmount = null;
        if (payload != null && payload.get("approvedAmount") != null) {
            Object amountObj = payload.get("approvedAmount");
            if (amountObj instanceof Number number) {
                approvedAmount = java.math.BigDecimal.valueOf(number.doubleValue());
            } else {
                approvedAmount = new java.math.BigDecimal(String.valueOf(amountObj));
            }
        }
        return tradeOrderService.approveRefund(id, sellerId, approvedAmount);
    }


    @PutMapping("/refund/return-tracking/{id}")
    public ResponseResult<?> submitReturnTracking(@PathVariable Long id,
                                                   @RequestBody java.util.Map<String, String> payload,
                                                   HttpServletRequest request) {
        Long buyerId = getUserId(request);
        if (buyerId == null) return ResponseResult.error(401, "请先登录");
        String returnTrackingNo = payload == null ? null : payload.get("returnTrackingNo");
        return tradeOrderService.submitReturnTracking(id, buyerId, returnTrackingNo);
    }

    @PutMapping("/refund/confirm-return/{id}")
    public ResponseResult<?> confirmReturnReceived(@PathVariable Long id,
                                                   @RequestBody(required = false) java.util.Map<String, Object> payload,
                                                   HttpServletRequest request) {
        Long sellerId = getUserId(request);
        if (sellerId == null) return ResponseResult.error(401, "请先登录");
        java.math.BigDecimal approvedAmount = null;
        if (payload != null && payload.get("approvedAmount") != null) {
            Object amountObj = payload.get("approvedAmount");
            if (amountObj instanceof Number number) {
                approvedAmount = java.math.BigDecimal.valueOf(number.doubleValue());
            } else {
                approvedAmount = new java.math.BigDecimal(String.valueOf(amountObj));
            }
        }
        return tradeOrderService.confirmReturnReceived(id, sellerId, approvedAmount);
    }


    @PutMapping("/cancel/{id}")
    public ResponseResult<?> cancelOrderByBuyer(@PathVariable Long id,
                                                 @RequestBody(required = false) java.util.Map<String, String> payload,
                                                 HttpServletRequest request) {
        Long buyerId = getUserId(request);
        if (buyerId == null) return ResponseResult.error(401, "请先登录");
        String reason = payload == null ? null : payload.get("reason");
        return tradeOrderService.cancelOrderByBuyer(id, buyerId, reason);
    }

    @GetMapping("/logistics/{id}")
    public ResponseResult<?> getLogisticsTrace(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseResult.error(401, "请先登录");
        return tradeOrderService.getLogisticsTrace(id, userId);
    }

    @GetMapping("/carriers")
    public ResponseResult<?> listCarriers() {
        java.util.List<java.util.Map<String, String>> carriers = java.util.List.of(
                java.util.Map.of("code", "SF", "name", "顺丰速运"),
                java.util.Map.of("code", "JD", "name", "京东物流"),
                java.util.Map.of("code", "EMS", "name", "中国邮政EMS"),
                java.util.Map.of("code", "ZTO", "name", "中通快递"),
                java.util.Map.of("code", "YTO", "name", "圆通速递"),
                java.util.Map.of("code", "STO", "name", "申通快递"),
                java.util.Map.of("code", "YUNDA", "name", "韵达速递"),
                java.util.Map.of("code", "DEPPON", "name", "德邦快递")
        );
        return ResponseResult.success(carriers);
    }

    @GetMapping("/notify-count")
    public ResponseResult<?> getNotifyCount(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseResult.error(401, "请先登录");

        LambdaQueryWrapper<TradeOrder> sellerQuery = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getSellerId, userId)
                .in(TradeOrder::getStatus, PAID_PENDING_SHIPMENT, REFUND_REQUESTED);
        long sellerCount = tradeOrderService.count(sellerQuery);

        LambdaQueryWrapper<TradeOrder> buyerQuery = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getBuyerId, userId)
                .eq(TradeOrder::getStatus, SHIPPED_PENDING_RECEIPT);
        long buyerCount = tradeOrderService.count(buyerQuery);

        return ResponseResult.success(sellerCount + buyerCount);
    }
}