package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;

    // ✅ 把原来的 Post 换成了 goodsId
    private Long goodsId;

    // ✅ 把原来的 User 对象关联换成了直接存 userId
    private Long userId;

    private Long parentId; // 楼层回复逻辑 (父评论 ID)

    private String content;

    private LocalDateTime createTime;
}