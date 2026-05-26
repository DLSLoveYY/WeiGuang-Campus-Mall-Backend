package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("goods_draft")
public class GoodsDraft {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sellerId; // 卖家ID (替代原 authorId)

    private String title;
    private String content; // 兼容前端字段名
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String category;
    private String deliveryType; // 兼容前端字段名
    private String deliveryMethods;
    private String coverImg; // 兼容前端字段名
    private String images;
    private Integer stock;
    private String conditionLevel;
    private String tradeAddress;
    private String variantsJson;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private List<GoodsVariant> variants;

    private LocalDateTime createTime;
}