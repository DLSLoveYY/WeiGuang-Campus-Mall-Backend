package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private String deliveryMethod; // 交易方式 (如：自提、校园面交、邮寄)

    private String deliveryAddress; // 收货地址/联系方式 (买家下单时填写的交接信息)

    /**
     * 担保交易核心状态机：
     * 0: 待支付 (买家刚拍下，尚未付款)
     * 1: 已支付，待发货/待面交 (【核心状态】买家已付款，钱暂存平台，等待卖家发货或联系面交)
     * 2: 已发货，待收货 (卖家已寄出快递或已赴约，等待买家拿到货品)
     * 3: 交易成功 (买家在线上点击“确认收货”，平台此时将钱打入卖家余额)
     * 4: 交易取消/关闭 (未支付前超时取消，或双方协商退款)
     */
    private Integer status = 0;

    private LocalDateTime createTime; // 下单时间

    private LocalDateTime payTime; // 买家付款时间

    private LocalDateTime deliveryTime; // 卖家发货/赴约时间

    private LocalDateTime finishTime; // 买家确认收货，平台放款的时间

    private LocalDateTime updateTime; // 订单状态最后一次更新的时间
}