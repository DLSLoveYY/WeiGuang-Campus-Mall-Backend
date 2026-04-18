package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

import static top.dlsloveyy.backendtest.constant.DisputeStatus.OPEN;

@Data
@TableName("trade_dispute")
public class TradeDispute {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long buyerId;

    private Long sellerId;

    private Integer status = OPEN;

    private String reason;

    private String buyerEvidence;

    private String sellerEvidence;

    private String resolution;

    private Long processorId;

    private LocalDateTime deadlineTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime closeTime;
}
