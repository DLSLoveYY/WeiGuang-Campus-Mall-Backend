package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trade_logistics_trace")
public class TradeLogisticsTrace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shipmentId;

    private String traceDesc;

    private String traceLocation;

    private LocalDateTime traceTime;

    private LocalDateTime createTime;
}
