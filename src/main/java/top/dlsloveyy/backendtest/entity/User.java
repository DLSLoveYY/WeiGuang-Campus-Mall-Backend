package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
@Entity
@Data
@Table(name = "user")
@TableName("user") // ✅ 新增：告诉 MyBatis-Plus 对应的表名
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO) // ✅ 新增：告诉 MyBatis-Plus 这是一个自增主键
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Integer points = 0;

    @Column(name = "is_admin", nullable = false)
    private Boolean isAdmin = false;

    @Column(nullable = false)
    private Boolean enabled = true; // 表示账号是否启用

    @Column(name = "avatar")
    private String avatar; // 头像图片URL

    @Column(length = 255)
    private String signature; // 个人签名

    @Column(length = 10)
    private String gender; // 性别（可为 "男", "女", "其他"）

    private Integer age; // 年龄

    // ================== 易交商城新增字段 ==================

    @Column(name = "contact_phone", length = 20)
    private String contactPhone; // 手机号

    @Column(name = "wechat_id", length = 50)
    private String wechatId; // 微信号（校园二手交易最常用的沟通方式）

    @Column(name = "dorm_building", length = 100)
    private String dormBuilding; // 宿舍楼栋/住址（例如：梅园1栋301，极大方便线下当面交易看货）

    @Column(name = "profile_lng", precision = 10, scale = 6)
    private BigDecimal profileLng;

    @Column(name = "profile_lat", precision = 10, scale = 6)
    private BigDecimal profileLat;

    @Column(name = "credit_score", nullable = false)
    private Integer creditScore = 100; // 默认信用分 100。未来可以做成：被举报/爽约扣分，交易成功加分

    // 账户余额（可用余额）
    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    // 冻结余额（用于担保交易、提现处理中等场景）
    @Column(nullable = false)
    private BigDecimal frozenBalance = BigDecimal.ZERO;
}