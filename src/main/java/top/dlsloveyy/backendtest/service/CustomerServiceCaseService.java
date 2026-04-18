package top.dlsloveyy.backendtest.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.dlsloveyy.backendtest.entity.CustomerServiceCase;
import top.dlsloveyy.backendtest.entity.CustomerServiceCaseAction;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;

import java.util.List;

public interface CustomerServiceCaseService extends IService<CustomerServiceCase> {

    ResponseResult<?> appendCaseAction(Long caseId,
                                       Long actorId,
                                       String actorRole,
                                       String actionType,
                                       String content,
                                       String attachments);

    ResponseResult<List<CustomerServiceCaseAction>> listCaseActions(Long caseId, Long operatorId, Boolean isAdmin);

    ResponseResult<?> createCase(Long orderId,
                                 Long disputeId,
                                 Long buyerId,
                                 Long sellerId,
                                 String source,
                                 String detail,
                                 Integer priority);

    ResponseResult<?> assignCase(Long caseId, Long adminId, Long operatorId);

    ResponseResult<?> resolveCase(Long caseId, Long operatorId, Integer decision, String resolution);

    ResponseResult<List<CustomerServiceCase>> listCases(Integer status, Long assignedAdminId);

    ResponseResult<List<CustomerServiceCase>> listMyCases(Long userId, Boolean asSeller);

    ResponseResult<CustomerServiceCase> getCaseDetail(Long caseId, Long operatorId, Boolean isAdmin);

    CustomerServiceCase getActiveCaseByDisputeId(Long disputeId);
}
