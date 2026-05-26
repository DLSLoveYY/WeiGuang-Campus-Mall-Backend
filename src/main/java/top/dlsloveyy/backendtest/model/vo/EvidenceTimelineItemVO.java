package top.dlsloveyy.backendtest.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EvidenceTimelineItemVO {
    private String sourceType;
    private String party;
    private String actionType;
    private String title;
    private String content;
    private List<String> attachments = new ArrayList<>();
    private String createTime;
}
