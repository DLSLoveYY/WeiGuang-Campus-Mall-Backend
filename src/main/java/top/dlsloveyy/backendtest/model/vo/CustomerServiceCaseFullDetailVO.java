package top.dlsloveyy.backendtest.model.vo;

import lombok.Data;
import top.dlsloveyy.backendtest.entity.CustomerServiceCase;
import top.dlsloveyy.backendtest.entity.CustomerServiceCaseAction;
import top.dlsloveyy.backendtest.entity.TradeDispute;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class CustomerServiceCaseFullDetailVO {
    private CustomerServiceCase caseInfo;
    private List<CustomerServiceCaseAction> actions = new ArrayList<>();
    private TradeDispute dispute;
    private OrderRefundInfoVO orderRefund;
    private List<EvidenceTimelineItemVO> evidenceTimeline = new ArrayList<>();

    @Data
    public static class OrderRefundInfoVO {
        private Long orderId;
        private String orderNo;
        private Integer orderStatus;
        private Integer refundType;
        private Integer refundStage;
        private BigDecimal refundRequestedAmount;
        private BigDecimal refundApprovedAmount;
        private String refundReason;
        private String refundReasonCode;
        private String refundApplyPacketRaw;
        private Map<String, Object> refundApplyPacketObj = new LinkedHashMap<>();
    }
}
