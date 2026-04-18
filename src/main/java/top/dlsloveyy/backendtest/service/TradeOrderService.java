package top.dlsloveyy.backendtest.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.dlsloveyy.backendtest.entity.TradeOrder;
import top.dlsloveyy.backendtest.model.dto.OrderCreateDTO;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.model.vo.TradeOrderVO;

import java.util.List;

public interface TradeOrderService extends IService<TradeOrder> {

    /**
     * 创建订单 (防超卖)
     */
    ResponseResult<?> createOrder(OrderCreateDTO dto, Long currentUserId);

    /**
     * 获取我买到的订单列表
     */
    ResponseResult<List<TradeOrderVO>> getMyPurchases(Long buyerId);

    /**
     * 获取我卖出的订单列表
     */
    ResponseResult<List<TradeOrderVO>> getMySales(Long sellerId);

    /**
     * 支付订单 (支持余额或第三方模拟支付)
     * 🚀 修复位置：这里加上了 String paymentMethod 参数
     */
    ResponseResult<?> payOrder(Long orderId, Long buyerId, String paymentMethod);

    /**
     * 确认发货
     */
    ResponseResult<?> shipOrder(Long orderId, Long sellerId);

    /**
     * 确认收货 (结算金额给卖家)
     */
    ResponseResult<?> receiveOrder(Long orderId, Long buyerId);

    // ==================== 逆向流程：退款相关接口 ====================

    /**
     * 申请退款 (买家操作)
     * @param orderId 订单ID
     * @param buyerId 买家ID (用于权限校验)
     * @param reason 退款/争议原因
     * @param buyerEvidence 证据（文本或上传文件URL拼接）
     */
    ResponseResult<?> applyRefund(Long orderId, Long buyerId, String reason, String buyerEvidence);

    /**
     * 同意退款 (卖家操作)
     * @param orderId 订单ID
     * @param sellerId 卖家ID (用于权限校验)
     */
    ResponseResult<?> approveRefund(Long orderId, Long sellerId);

    /**
     * 拒绝退款 (卖家操作)
     * @param orderId 订单ID
     * @param sellerId 卖家ID (用于权限校验)
     */
    ResponseResult<?> rejectRefund(Long orderId, Long sellerId);

    int closeExpiredPendingOrders();
}