package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_follow") // 对应数据库里生成的关注表
public class Follow {

    @TableId(type = IdType.AUTO)
    private Long id;

    // ✅ 把原本的 User 对象换成了直接存 ID
    private Long followerId; // 关注者的ID (粉丝)
    private Long followeeId; // 被关注者的ID (博主/卖家)

    private LocalDateTime createTime;
}