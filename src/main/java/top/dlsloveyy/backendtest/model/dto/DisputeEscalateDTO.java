package top.dlsloveyy.backendtest.model.dto;

import lombok.Data;

@Data
public class DisputeEscalateDTO {
    private Long orderId;
    private String reason;
    private String buyerEvidence;
    private Integer priority;
}
