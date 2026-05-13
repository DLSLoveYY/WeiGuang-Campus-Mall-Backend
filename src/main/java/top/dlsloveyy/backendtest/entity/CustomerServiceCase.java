package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

import static top.dlsloveyy.backendtest.constant.CustomerServiceCaseStatus.PENDING_ASSIGN;

@Data
@TableName("customer_service_case")
public class CustomerServiceCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String caseNo;

    private Long orderId;

    private Long disputeId;

    private Long buyerId;

    private Long sellerId;

    private Integer status = PENDING_ASSIGN;

    private Integer priority;

    private String source;

    private String requestTitle;

    private Long assignedAdminId;

    private String latestAction;

    private LocalDateTime slaDeadlineTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime closeTime;
}
