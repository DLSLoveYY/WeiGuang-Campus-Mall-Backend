package top.dlsloveyy.backendtest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.dlsloveyy.backendtest.entity.AccountLedger;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.AccountLedgerMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.service.AccountService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountServiceImpl extends ServiceImpl<AccountLedgerMapper, AccountLedger> implements AccountService {

    @Autowired
    private AccountLedgerMapper accountLedgerMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void debit(Long userId,
                      BigDecimal amount,
                      String bizType,
                      String bizNo,
                      String idempotencyKey,
                      String remark) {
        process(userId, amount, bizType, bizNo, "OUT", idempotencyKey, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void credit(Long userId,
                       BigDecimal amount,
                       String bizType,
                       String bizNo,
                       String idempotencyKey,
                       String remark) {
        process(userId, amount, bizType, bizNo, "IN", idempotencyKey, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long userId,
                       BigDecimal amount,
                       String bizType,
                       String bizNo,
                       String idempotencyKey,
                       String remark) {
        process(userId, amount, bizType, bizNo, "FREEZE", idempotencyKey, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(Long userId,
                         BigDecimal amount,
                         String bizType,
                         String bizNo,
                         String idempotencyKey,
                         String remark) {
        process(userId, amount, bizType, bizNo, "UNFREEZE", idempotencyKey, remark);
    }

    @Override
    public ResponseResult<?> listLedger(Long userId) {
        List<AccountLedger> list = accountLedgerMapper.selectList(new LambdaQueryWrapper<AccountLedger>()
                .eq(AccountLedger::getUserId, userId)
                .orderByDesc(AccountLedger::getCreateTime));
        return ResponseResult.success(list);
    }

    @Override
    public Map<String, BigDecimal> getBalanceSnapshot(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("账户不存在");
        }
        BigDecimal available = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
        BigDecimal frozen = user.getFrozenBalance() == null ? BigDecimal.ZERO : user.getFrozenBalance();
        Map<String, BigDecimal> map = new HashMap<>();
        map.put("availableBalance", available);
        map.put("frozenBalance", frozen);
        map.put("totalBalance", available.add(frozen));
        return map;
    }

    private void process(Long userId,
                         BigDecimal amount,
                         String bizType,
                         String bizNo,
                         String changeType,
                         String idempotencyKey,
                         String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("金额必须大于0");
        }
        AccountLedger existed = accountLedgerMapper.selectOne(new LambdaQueryWrapper<AccountLedger>()
                .eq(AccountLedger::getIdempotencyKey, idempotencyKey)
                .last("limit 1"));
        if (existed != null) {
            return;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("账户不存在");
        }

        BigDecimal before = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
        BigDecimal frozenBefore = user.getFrozenBalance() == null ? BigDecimal.ZERO : user.getFrozenBalance();
        BigDecimal after = before;
        BigDecimal frozenAfter = frozenBefore;

        switch (changeType) {
            case "OUT" -> {
                if (before.compareTo(amount) < 0) {
                    throw new RuntimeException("账户余额不足");
                }
                after = before.subtract(amount);
                user.setBalance(after);
            }
            case "IN" -> {
                after = before.add(amount);
                user.setBalance(after);
            }
            case "FREEZE" -> {
                if (isEscrowHoldBizType(bizType)) {
                    // 担保交易场景：订单支付后为卖家挂起待结算资金，不占用卖家已有可用余额
                    frozenAfter = frozenBefore.add(amount);
                    user.setFrozenBalance(frozenAfter);
                } else {
                    if (before.compareTo(amount) < 0) {
                        throw new RuntimeException("可用余额不足，无法冻结");
                    }
                    after = before.subtract(amount);
                    frozenAfter = frozenBefore.add(amount);
                    user.setBalance(after);
                    user.setFrozenBalance(frozenAfter);
                }
            }
            case "UNFREEZE" -> {
                if (frozenBefore.compareTo(amount) < 0) {
                    throw new RuntimeException("冻结余额不足，无法解冻");
                }
                if (isRefundConsumeBizType(bizType)) {
                    // 退款场景：消耗卖家待结算冻结资金，不回到卖家可用余额
                    frozenAfter = frozenBefore.subtract(amount);
                    user.setFrozenBalance(frozenAfter);
                } else {
                    after = before.add(amount);
                    frozenAfter = frozenBefore.subtract(amount);
                    user.setBalance(after);
                    user.setFrozenBalance(frozenAfter);
                }
            }
            default -> throw new RuntimeException("不支持的资金变动类型");
        }

        userMapper.updateById(user);

        AccountLedger ledger = new AccountLedger();
        ledger.setUserId(userId);
        ledger.setBizType(bizType);
        ledger.setBizNo(bizNo);
        ledger.setChangeType(changeType);
        ledger.setAmount(amount);
        ledger.setBalanceBefore(before);
        ledger.setBalanceAfter(after);
        ledger.setFrozenBefore(frozenBefore);
        ledger.setFrozenAfter(frozenAfter);
        ledger.setIdempotencyKey(idempotencyKey);
        ledger.setRemark(remark);
        ledger.setCreateTime(LocalDateTime.now());
        accountLedgerMapper.insert(ledger);
    }

    private boolean isEscrowHoldBizType(String bizType) {
        return "ORDER_SETTLE_FROZEN".equals(bizType);
    }

    private boolean isRefundConsumeBizType(String bizType) {
        return "ORDER_REFUND_UNFREEZE".equals(bizType)
                || "DISPUTE_REFUND_UNFREEZE".equals(bizType)
                || "CS_CASE_REFUND_UNFREEZE".equals(bizType);
    }
}
