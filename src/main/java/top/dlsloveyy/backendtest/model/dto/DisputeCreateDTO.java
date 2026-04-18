package top.dlsloveyy.backendtest.model.dto;

import lombok.Data;

@Data
public class DisputeCreateDTO {
    private Long orderId;
    private String reason;
    private String buyerEvidence;
}
