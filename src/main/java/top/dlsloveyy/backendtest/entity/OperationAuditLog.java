package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_audit_log")
public class OperationAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long actorId;

    private String actorRole;

    private String action;

    private String resourceType;

    private String resourceId;

    private String detail;

    private LocalDateTime createTime;
}
