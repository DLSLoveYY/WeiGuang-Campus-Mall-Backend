package top.dlsloveyy.backendtest.service;

import top.dlsloveyy.backendtest.model.dto.ResponseResult;

import java.math.BigDecimal;
import java.util.Map;

public interface AccountService {

    void debit(Long userId,
               BigDecimal amount,
               String bizType,
               String bizNo,
               String idempotencyKey,
               String remark);

    void credit(Long userId,
                BigDecimal amount,
                String bizType,
                String bizNo,
                String idempotencyKey,
                String remark);

    void freeze(Long userId,
                BigDecimal amount,
                String bizType,
                String bizNo,
                String idempotencyKey,
                String remark);

    void unfreeze(Long userId,
                  BigDecimal amount,
                  String bizType,
                  String bizNo,
                  String idempotencyKey,
                  String remark);

    ResponseResult<?> listLedger(Long userId);

    Map<String, BigDecimal> getBalanceSnapshot(Long userId);
}
