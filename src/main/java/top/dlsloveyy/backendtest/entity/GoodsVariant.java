package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("goods_variant")
public class GoodsVariant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long goodsId;

    private String variantName;

    private String optionName;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private String description;

    private Integer sortOrder;

    private Boolean enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
