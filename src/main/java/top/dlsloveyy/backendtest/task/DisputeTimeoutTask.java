package top.dlsloveyy.backendtest.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.dlsloveyy.backendtest.constant.DisputeStatus;
import top.dlsloveyy.backendtest.entity.CustomerServiceCase;
import top.dlsloveyy.backendtest.entity.TradeDispute;
import top.dlsloveyy.backendtest.entity.TradeOrder;
import top.dlsloveyy.backendtest.mapper.TradeDisputeMapper;
import top.dlsloveyy.backendtest.mapper.TradeOrderMapper;
import top.dlsloveyy.backendtest.service.CustomerServiceCaseService;
import top.dlsloveyy.backendtest.service.UserNotificationService;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class DisputeTimeoutTask {

    @Autowired
    private TradeDisputeMapper tradeDisputeMapper;

    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    @Autowired
    private CustomerServiceCaseService customerServiceCaseService;

    @Autowired
    private UserNotificationService userNotificationService;

    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void escalateExpiredDisputes() {
        LocalDateTime now = LocalDateTime.now();
        List<TradeDispute> expired = tradeDisputeMapper.selectList(new LambdaQueryWrapper<TradeDispute>()
                .in(TradeDispute::getStatus, DisputeStatus.OPEN, DisputeStatus.IN_REVIEW)
                .isNotNull(TradeDispute::getDeadlineTime)
                .lt(TradeDispute::getDeadlineTime, now));

        int handled = 0;
        for (TradeDispute dispute : expired) {
            CustomerServiceCase activeCase = customerServiceCaseService.getActiveCaseByDisputeId(dispute.getId());
            if (activeCase != null) {
                continue;
            }

            TradeOrder order = tradeOrderMapper.selectById(dispute.getOrderId());
            if (order == null) {
                continue;
            }

            userNotificationService.notifyUser(dispute.getBuyerId(),
                    "DISPUTE_TIMEOUT",
                    "您的争议已超期，已转入客服介入",
                    "订单 " + order.getId() + " 的争议已超过处理时限，系统已自动升级到客服介入。",
                    "TRADE_DISPUTE",
                    dispute.getId());
            userNotificationService.notifyUser(dispute.getSellerId(),
                    "DISPUTE_TIMEOUT",
                    "争议已超期，已转入客服介入",
                    "订单 " + order.getId() + " 的争议已超过处理时限，系统已升级到客服介入。",
                    "TRADE_DISPUTE",
                    dispute.getId());

            customerServiceCaseService.createCase(
                    order.getId(),
                    dispute.getId(),
                    dispute.getBuyerId(),
                    dispute.getSellerId(),
                    "DISPUTE_TIMEOUT",
                    "争议单超期未处理，系统自动升级客服介入",
                    2);
            handled++;
        }

        if (handled > 0) {
            log.info("Escalated expired disputes count={}", handled);
        }
    }
}
