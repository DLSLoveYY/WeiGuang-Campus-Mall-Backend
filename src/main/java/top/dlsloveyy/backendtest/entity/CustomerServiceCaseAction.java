package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("customer_service_case_action")
public class CustomerServiceCaseAction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long caseId;

    private Long actorId;

    private String actorRole;

    private String actionType;

    private String content;

    private String attachments;

    private LocalDateTime createTime;
}
