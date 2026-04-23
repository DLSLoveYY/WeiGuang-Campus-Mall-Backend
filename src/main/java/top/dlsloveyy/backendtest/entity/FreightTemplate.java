package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("freight_template")
public class FreightTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sellerId;

    private String templateName;

    private BigDecimal baseFee;

    private BigDecimal freeShippingThreshold;

    private BigDecimal extraFeePerItem;

    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
