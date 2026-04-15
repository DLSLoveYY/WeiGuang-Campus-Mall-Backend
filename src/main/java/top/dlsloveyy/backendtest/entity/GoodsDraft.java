package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private String coverImg; // 兼容前端字段名

    private LocalDateTime createTime;
}