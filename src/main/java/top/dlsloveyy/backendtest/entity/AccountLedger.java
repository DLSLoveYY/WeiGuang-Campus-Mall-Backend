package top.dlsloveyy.backendtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("account_ledger")
public class AccountLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String bizType;

    private String bizNo;

    private String changeType;

    private BigDecimal amount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private BigDecimal frozenBefore;

    private BigDecimal frozenAfter;

    private String idempotencyKey;

    private String remark;

    private LocalDateTime createTime;
}
