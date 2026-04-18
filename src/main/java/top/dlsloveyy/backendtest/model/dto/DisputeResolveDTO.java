package top.dlsloveyy.backendtest.model.dto;

import lombok.Data;

@Data
public class DisputeResolveDTO {
    private Long disputeId;
    private Integer decision;
    private String resolution;
}
