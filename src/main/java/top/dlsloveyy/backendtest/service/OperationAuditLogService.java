package top.dlsloveyy.backendtest.service;

public interface OperationAuditLogService {
    void log(Long actorId,
             String actorRole,
             String action,
             String resourceType,
             String resourceId,
             String detail);
}
