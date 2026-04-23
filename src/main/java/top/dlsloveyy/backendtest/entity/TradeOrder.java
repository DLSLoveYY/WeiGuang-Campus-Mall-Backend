package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static top.dlsloveyy.backendtest.constant.OrderStatus.PENDING_PAYMENT;

@Data
@TableName("trade_order")
public class TradeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo; // 订单流水号 (建议用雪花算法或UUID生成)

    private String paymentNo; // 第三方支付流水号 (如支付宝沙箱交易号)

    private Long buyerId; // 买家 ID

    private Long sellerId; // 卖家 ID

    private Long goodsId; // 关联的商品ID

    // --- 财务对账核心字段 ---
    private BigDecimal orderPrice; // 买家实际支付的总金额

    private BigDecimal platformFee; // 平台抽成/手续费金额

    private BigDecimal sellerIncome; // 卖家预计可得收入 (orderPrice - platformFee)

    // --- 物流与交接核心字段 ---
    private String deliveryMethod; // 交易方式（校园面交、邮寄）

    private String deliveryAddress; // 收货地址/联系方式快照

    private String meetupAddress; // 买家填写的详细面交地址

    private String meetupPhone; // 买家填写的确认手机号

    private String sellerConfirmPhoneSuffix; // 卖家确认时输入的买家手机号后4位

    private LocalDateTime handoffConfirmTime; // 校园面交确认交货时间

    private String cancelReasonCode; // 关闭/取消原因码

    private String cancelReasonDesc; // 关闭/取消原因描述

    private String cancelSource; // 关闭来源：SYSTEM/BUYER/SELLER/ADMIN

    private Integer refundType; // 1仅退款 2退货退款 3部分退款

    private Integer refundStage; // 0无 1申请中 2卖家处理中 3买家退货中 4卖家确认收货中 5客服/平台处理中 6完成 7拒绝

    private BigDecimal refundRequestedAmount; // 买家申请退款金额

    private BigDecimal refundApprovedAmount; // 卖家/平台批准退款金额

    private String refundReason; // 退款原因

    private String refundReasonCode; // 退款原因编码（用于面板化申请）

    private String refundApplyPacket; // 退款申请报文（JSON字符串，供管理员审核）

    private String returnTrackingNo; // 退货物流单号

    private String carrierCode; // 正向发货物流公司编码

    private String trackingNo; // 正向发货物流单号

    private BigDecimal freightFee; // 运费金额

    private Long addressId; // 地址簿ID

    /**
     * 担保交易核心状态机：
     * 0: 待支付
     * 1: 已支付待发货
     * 2: 已发货待收货
     * 3: 交易成功
     * 4: 交易关闭
     * 5: 已退款
     * 6: 退款申请中
     */
    private Integer status = PENDING_PAYMENT;

    private LocalDateTime createTime; // 下单时间

    private LocalDateTime payTime; // 买家付款时间

    private LocalDateTime deliveryTime; // 卖家发货/赴约时间

    private LocalDateTime finishTime; // 买家确认收货，平台放款的时间

    private LocalDateTime updateTime; // 订单状态最后一次更新的时间

    private LocalDateTime cancelTime; // 订单取消/关闭时间
}