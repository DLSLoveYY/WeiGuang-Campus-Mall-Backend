package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交易评价实体
 * 买家对卖家评价（role=buyer），交易完成（status=3）后各方仅可评一次
 */
@Entity
@Data
@Table(name = "trade_review")
@TableName("trade_review")
public class TradeReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private Long revieweeId;

    @Column(nullable = false)
    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 20)
    private String role;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}

