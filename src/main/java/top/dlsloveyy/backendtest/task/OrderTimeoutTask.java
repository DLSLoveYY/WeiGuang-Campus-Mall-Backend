package top.dlsloveyy.backendtest.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.dlsloveyy.backendtest.service.TradeOrderService;

@Slf4j
@Component
public class OrderTimeoutTask {

    @Autowired
    private TradeOrderService tradeOrderService;

    @Scheduled(cron = "0 */1 * * * ?")
    public void closeExpiredPendingOrders() {
        int closedCount = tradeOrderService.closeExpiredPendingOrders();
        if (closedCount > 0) {
            log.info("Closed expired pending orders count={}", closedCount);
        }
    }
}
