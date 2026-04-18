package top.dlsloveyy.backendtest.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dlsloveyy.backendtest.entity.OperationAuditLog;
import top.dlsloveyy.backendtest.mapper.OperationAuditLogMapper;
import top.dlsloveyy.backendtest.service.OperationAuditLogService;

import java.time.LocalDateTime;

@Service
public class OperationAuditLogServiceImpl implements OperationAuditLogService {

    @Autowired
    private OperationAuditLogMapper operationAuditLogMapper;

    @Override
    public void log(Long actorId,
                    String actorRole,
                    String action,
                    String resourceType,
                    String resourceId,
                    String detail) {
        OperationAuditLog record = new OperationAuditLog();
        record.setActorId(actorId);
        record.setActorRole(actorRole);
        record.setAction(action);
        record.setResourceType(resourceType);
        record.setResourceId(resourceId);
        record.setDetail(detail);
        record.setCreateTime(LocalDateTime.now());
        operationAuditLogMapper.insert(record);
    }
}
