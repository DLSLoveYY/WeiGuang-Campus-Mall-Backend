package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trade_shipment")
public class TradeShipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long sellerId;

    private Long buyerId;

    private String carrierCode;

    private String trackingNo;

    private Integer status;

    private LocalDateTime shippedTime;

    private LocalDateTime deliveredTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
