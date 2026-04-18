package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "goods")
@TableName("goods") // MyBatis-Plus 注解
public class Goods {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Long sellerId; // 卖家用户ID

    @Column(nullable = false, length = 100)
    private String title; // 商品标题 (如：几乎全新的苹果 iPad Pro)

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description; // 商品详细描述

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price; // 售价（二手价）

    @Column(precision = 10, scale = 2)
    private BigDecimal originalPrice; // 原价（展示折扣用，吸引买家）

    @Column(nullable = false)
    private String category; // 分类 (如：数码、书籍、日用、美妆、代步工具)

    @Column(length = 20)
    private String conditionLevel; // 新旧程度 (如：全新、9成新、有使用痕迹)

    @Column(columnDefinition = "TEXT")
    private String images; // 商品图片集（前端可传多个图片URL，用逗号拼接存入）

    @Column(nullable = false)
    private Integer stock = 1; // ✅ 新增：商品库存，默认值为 1

    @Column(nullable = false)
    private Integer status = 0; // 商品状态：0-待审核, 1-出售中, 2-已预订, 3-已售出, 4-已下架

    @Column(length = 50)
    private String deliveryMethod; // 交货方式 (如：食堂面交、宿舍楼下自提、快递)

    @Column(nullable = false)
    private Integer viewCount = 0; // 浏览量

    @Column(nullable = false)
    private LocalDateTime createTime; // 发布时间

    @Column(nullable = false)
    private LocalDateTime updateTime; // 最后更新时间

    @Column(nullable = false)
    private Integer favoritesCount = 0; // 想要数/收藏数 (替代原来的 likes)

    @Column(nullable = false)
    private Integer commentCount = 0; // 留言/提问数量 (对应原 comments)

    @Column(nullable = false)
    private Boolean isFeatured = false; // 是否为精选/推荐商品 (对应原 featured)

    @Transient
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String sellerName;

    @Transient
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String sellerAvatar;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}