package top.dlsloveyy.backendtest.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.model.dto.OrderCreateDTO;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.service.TradeOrderService;
import top.dlsloveyy.backendtest.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;

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
    public ResponseResult<?> shipOrder(@PathVariable Long id, HttpServletRequest request) {
        Long sellerId = getUserId(request);
        if (sellerId == null) return ResponseResult.error(401, "请先登录");
        return tradeOrderService.shipOrder(id, sellerId);
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
    public ResponseResult<?> applyRefund(@PathVariable Long id, HttpServletRequest request) {
        Long buyerId = getUserId(request);
        if (buyerId == null) return ResponseResult.error(401, "请先登录");
        return tradeOrderService.applyRefund(id, buyerId);
    }

    /**
     * 同意退款 (卖家操作)
     */
    @PutMapping("/refund/approve/{id}")
    public ResponseResult<?> approveRefund(@PathVariable Long id, HttpServletRequest request) {
        Long sellerId = getUserId(request);
        if (sellerId == null) return ResponseResult.error(401, "请先登录");
        return tradeOrderService.approveRefund(id, sellerId);
    }

    /**
     * 拒绝退款 (卖家操作)
     */
    @PutMapping("/refund/reject/{id}")
    public ResponseResult<?> rejectRefund(@PathVariable Long id, HttpServletRequest request) {
        Long sellerId = getUserId(request);
        if (sellerId == null) return ResponseResult.error(401, "请先登录");
        return tradeOrderService.rejectRefund(id, sellerId);
    }
}