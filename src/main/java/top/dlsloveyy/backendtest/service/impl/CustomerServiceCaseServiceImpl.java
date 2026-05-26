package top.dlsloveyy.backendtest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.dlsloveyy.backendtest.entity.CustomerServiceCase;
import top.dlsloveyy.backendtest.entity.CustomerServiceCaseAction;
import top.dlsloveyy.backendtest.mapper.CustomerServiceCaseActionMapper;
import top.dlsloveyy.backendtest.mapper.CustomerServiceCaseMapper;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.model.vo.CustomerServiceCaseFullDetailVO;
import top.dlsloveyy.backendtest.model.vo.EvidenceTimelineItemVO;
import top.dlsloveyy.backendtest.service.AccountService;
import top.dlsloveyy.backendtest.service.CustomerServiceCaseService;
import top.dlsloveyy.backendtest.service.OperationAuditLogService;
import top.dlsloveyy.backendtest.service.UserNotificationService;
import top.dlsloveyy.backendtest.entity.TradeDispute;
import top.dlsloveyy.backendtest.entity.TradeOrder;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.TradeDisputeMapper;
import top.dlsloveyy.backendtest.mapper.TradeOrderMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static top.dlsloveyy.backendtest.constant.CustomerServiceCaseStatus.IN_PROGRESS;
import static top.dlsloveyy.backendtest.constant.CustomerServiceCaseStatus.PENDING_ASSIGN;
import static top.dlsloveyy.backendtest.constant.CustomerServiceCaseStatus.RESOLVED;
import static top.dlsloveyy.backendtest.constant.CustomerServiceCaseStatus.WAITING_EVIDENCE;
import static top.dlsloveyy.backendtest.constant.DisputeStatus.BUYER_WON;
import static top.dlsloveyy.backendtest.constant.DisputeStatus.SELLER_WON;
@Service
public class CustomerServiceCaseServiceImpl extends ServiceImpl<CustomerServiceCaseMapper, CustomerServiceCase>
        implements CustomerServiceCaseService {

    @Autowired
    private CustomerServiceCaseMapper customerServiceCaseMapper;

    @Autowired
    private CustomerServiceCaseActionMapper caseActionMapper;

    @Autowired
    private OperationAuditLogService operationAuditLogService;

    @Autowired
    private UserNotificationService userNotificationService;

    @Autowired
    private TradeDisputeMapper tradeDisputeMapper;

    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> appendCaseAction(Long caseId,
                                              Long actorId,
                                              String actorRole,
                                              String actionType,
                                              String content,
                                              String attachments) {
        CustomerServiceCase csCase = customerServiceCaseMapper.selectById(caseId);
        if (csCase == null) {
            return ResponseResult.error("工单不存在");
        }
        if (csCase.getStatus() == RESOLVED || csCase.getStatus() == top.dlsloveyy.backendtest.constant.CustomerServiceCaseStatus.CLOSED) {
            return ResponseResult.error("工单已结案，无法继续补充");
        }

        writeAction(caseId, actorId, actorRole, actionType, content, attachments);
        csCase.setLatestAction(content == null ? actionType : content);
        csCase.setUpdateTime(LocalDateTime.now());
        if ("REQUEST_EVIDENCE".equals(actionType)) {
            csCase.setStatus(WAITING_EVIDENCE);
            userNotificationService.notifyUser(
                    csCase.getBuyerId(),
                    "CS_REQUEST_EVIDENCE",
                    "客服要求补充证据",
                    "客服已要求补充交易凭证，请尽快进入工单补充说明与附件。",
                    "CUSTOMER_SERVICE_CASE",
                    caseId
            );
            if (csCase.getSellerId() != null && csCase.getSellerId() > 0) {
                userNotificationService.notifyUser(
                        csCase.getSellerId(),
                        "CS_REQUEST_EVIDENCE",
                        "客服要求补充证据",
                        "客服已要求补充交易凭证，请尽快进入工单补充说明与附件。",
                        "CUSTOMER_SERVICE_CASE",
                        caseId
                );
            }
        } else if ("EVIDENCE_ADDED".equals(actionType) && csCase.getStatus() == WAITING_EVIDENCE) {
            csCase.setStatus(IN_PROGRESS);
            Long notifyTarget = "ADMIN".equalsIgnoreCase(actorRole) ? csCase.getBuyerId() : csCase.getAssignedAdminId();
            if (notifyTarget != null && notifyTarget > 0) {
                userNotificationService.notifyUser(
                        notifyTarget,
                        "CS_EVIDENCE_ADDED",
                        "工单有新证据",
                        "有用户补充了新的交易证据，请及时查看。",
                        "CUSTOMER_SERVICE_CASE",
                        caseId
                );
            }
        }
        customerServiceCaseMapper.updateById(csCase);
        return ResponseResult.success("工单记录已更新");
    }

    @Override
    public ResponseResult<List<CustomerServiceCaseAction>> listCaseActions(Long caseId, Long operatorId, Boolean isAdmin) {
        CustomerServiceCase csCase = customerServiceCaseMapper.selectById(caseId);
        if (csCase == null) {
            return ResponseResult.error("工单不存在");
        }
        if (!Boolean.TRUE.equals(isAdmin)
                && !operatorId.equals(csCase.getBuyerId())
                && !operatorId.equals(csCase.getSellerId())) {
            return ResponseResult.error(403, "无权查看该工单");
        }

        List<CustomerServiceCaseAction> actions = caseActionMapper.selectList(new LambdaQueryWrapper<CustomerServiceCaseAction>()
                .eq(CustomerServiceCaseAction::getCaseId, caseId)
                .orderByAsc(CustomerServiceCaseAction::getCreateTime));
        return ResponseResult.success(actions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createCase(Long orderId,
                                        Long disputeId,
                                        Long buyerId,
                                        Long sellerId,
                                        String source,
                                        String detail,
                                        Integer priority) {
        if (disputeId != null && disputeId > 0) {
            CustomerServiceCase existed = getActiveCaseByDisputeId(disputeId);
            if (existed != null) {
                return ResponseResult.success("该争议已存在客服工单", existed.getId());
            }
        }

        CustomerServiceCase csCase = new CustomerServiceCase();
        csCase.setCaseNo("CS" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        csCase.setOrderId(orderId == null ? 0L : orderId);
        csCase.setDisputeId(disputeId == null ? 0L : disputeId);
        csCase.setBuyerId(buyerId);
        csCase.setSellerId(sellerId == null ? 0L : sellerId);
        csCase.setSource(source);
        csCase.setPriority(priority == null ? 2 : priority);
        csCase.setStatus(PENDING_ASSIGN);
        csCase.setLatestAction("买家发起客服介入");
        csCase.setCreateTime(LocalDateTime.now());
        csCase.setUpdateTime(LocalDateTime.now());
        csCase.setSlaDeadlineTime(LocalDateTime.now().plusHours(24));
        customerServiceCaseMapper.insert(csCase);

        writeAction(csCase.getId(), buyerId, "USER", "CREATE", detail, null);
        operationAuditLogService.log(
                buyerId,
                "USER",
                "CS_CASE_CREATED",
                "CUSTOMER_SERVICE_CASE",
                String.valueOf(csCase.getId()),
                "orderId=" + orderId + ", disputeId=" + disputeId + ", status=" + PENDING_ASSIGN
        );

        return ResponseResult.success("已提交客服介入", csCase.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createUserRequest(Long buyerId,
                                               Long orderId,
                                               String category,
                                               String title,
                                               String detail,
                                               String attachments,
                                               Integer priority) {
        if (buyerId == null) {
            return ResponseResult.error("请先登录");
        }
        if (title == null || title.trim().isEmpty()) {
            return ResponseResult.error("请填写问题标题");
        }
        if (detail == null || detail.trim().isEmpty()) {
            return ResponseResult.error("请填写问题描述");
        }

        CustomerServiceCase csCase = new CustomerServiceCase();
        csCase.setCaseNo("REQ" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        csCase.setOrderId(orderId == null ? 0L : orderId);
        csCase.setDisputeId(0L);
        csCase.setBuyerId(buyerId);
        csCase.setSellerId(0L);
        csCase.setSource(category);
        csCase.setRequestTitle(title.trim());
        csCase.setPriority(priority == null ? 2 : priority);
        csCase.setStatus(PENDING_ASSIGN);
        csCase.setLatestAction("用户请求：" + title.trim());
        csCase.setCreateTime(LocalDateTime.now());
        csCase.setUpdateTime(LocalDateTime.now());
        csCase.setSlaDeadlineTime(LocalDateTime.now().plusHours(24));
        customerServiceCaseMapper.insert(csCase);

        writeAction(csCase.getId(), buyerId, "USER", "REQUEST", detail, attachments);
        operationAuditLogService.log(
                buyerId,
                "USER",
                "CS_REQUEST_CREATED",
                "CUSTOMER_SERVICE_CASE",
                String.valueOf(csCase.getId()),
                "category=" + category + ", title=" + title
        );

        return ResponseResult.success("已提交人工客服请求", csCase.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> assignCase(Long caseId, Long adminId, Long operatorId) {
        CustomerServiceCase csCase = customerServiceCaseMapper.selectById(caseId);
        if (csCase == null) {
            return ResponseResult.error("工单不存在");
        }
        if (csCase.getStatus() == RESOLVED || csCase.getStatus() == top.dlsloveyy.backendtest.constant.CustomerServiceCaseStatus.CLOSED) {
            return ResponseResult.error("工单已结案，无法接单");
        }

        csCase.setAssignedAdminId(adminId);
        csCase.setStatus(IN_PROGRESS);
        csCase.setLatestAction("工单已分配");
        csCase.setUpdateTime(LocalDateTime.now());
        customerServiceCaseMapper.updateById(csCase);

        writeAction(caseId, operatorId, "ADMIN", "ASSIGN", "assignTo=" + adminId, null);
        operationAuditLogService.log(
                operatorId,
                "ADMIN",
                "CS_CASE_ASSIGNED",
                "CUSTOMER_SERVICE_CASE",
                String.valueOf(caseId),
                "assignedAdminId=" + adminId
        );
        return ResponseResult.success("工单分配成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> resolveCase(Long caseId, Long operatorId, Integer decision, String resolution) {
        CustomerServiceCase csCase = customerServiceCaseMapper.selectById(caseId);
        if (csCase == null) {
            return ResponseResult.error("工单不存在");
        }
        if (csCase.getDisputeId() == null || csCase.getDisputeId() <= 0) {
            return ResponseResult.error("工单未关联争议单");
        }

        if (decision == null || (decision != BUYER_WON && decision != SELLER_WON)) {
            return ResponseResult.error("无效裁决结果");
        }

        TradeDispute dispute = tradeDisputeMapper.selectById(csCase.getDisputeId());
        if (dispute == null) {
            return ResponseResult.error("关联争议单不存在");
        }
        if (dispute.getStatus() == BUYER_WON || dispute.getStatus() == SELLER_WON || dispute.getStatus() == top.dlsloveyy.backendtest.constant.DisputeStatus.CLOSED) {
            return ResponseResult.error("争议单已处理完成");
        }

        TradeOrder order = tradeOrderMapper.selectById(dispute.getOrderId());
        if (order == null) {
            return ResponseResult.error("关联订单不存在");
        }

        dispute.setStatus(decision);
        dispute.setResolution(resolution);
        dispute.setProcessorId(operatorId);
        dispute.setUpdateTime(LocalDateTime.now());
        dispute.setCloseTime(LocalDateTime.now());
        tradeDisputeMapper.updateById(dispute);

        if (decision == BUYER_WON) {
            order.setStatus(top.dlsloveyy.backendtest.constant.OrderStatus.REFUNDED);
            order.setRefundStage(6);
            order.setUpdateTime(LocalDateTime.now());
            tradeOrderMapper.updateById(order);

            BigDecimal escrowAmount = resolveEscrowAmount(order);
            if (escrowAmount.compareTo(BigDecimal.ZERO) > 0) {
                accountService.unfreeze(
                        order.getSellerId(),
                        escrowAmount,
                        "CS_CASE_REFUND_UNFREEZE",
                        String.valueOf(order.getId()),
                        "CS_CASE_REFUND_UNFREEZE:" + order.getId() + ":" + order.getSellerId(),
                        "客服裁决退款，释放卖家待结算金额"
                );
            }

            accountService.credit(
                    order.getBuyerId(),
                    order.getOrderPrice(),
                    "CS_CASE_REFUND",
                    String.valueOf(order.getId()),
                    "CS_CASE_REFUND:IN:" + order.getId() + ":" + order.getBuyerId(),
                    "客服裁决买家胜，退款入账"
            );
        } else {
            LocalDateTime now = LocalDateTime.now();
            order.setStatus(top.dlsloveyy.backendtest.constant.OrderStatus.COMPLETED);
            order.setRefundStage(7);
            order.setUpdateTime(now);
            order.setFinishTime(now);
            tradeOrderMapper.updateById(order);

            BigDecimal escrowAmount = resolveEscrowAmount(order);
            if (escrowAmount.compareTo(BigDecimal.ZERO) > 0) {
                accountService.unfreeze(
                        order.getSellerId(),
                        escrowAmount,
                        "ORDER_SETTLE_UNFROZEN",
                        String.valueOf(order.getId()),
                        "ORDER_SETTLE_UNFROZEN:" + order.getId() + ":" + order.getSellerId(),
                        "客服裁决卖家胜，强制完成订单并结算"
                );
            }
        }

        operationAuditLogService.log(
                operatorId,
                "ADMIN",
                "DISPUTE_RESOLVED",
                "TRADE_DISPUTE",
                String.valueOf(dispute.getId()),
                "decision=" + decision + ", orderStatus=" + order.getStatus()
        );

        csCase.setStatus(RESOLVED);
        csCase.setLatestAction("工单已处理");
        csCase.setUpdateTime(LocalDateTime.now());
        csCase.setCloseTime(LocalDateTime.now());
        customerServiceCaseMapper.updateById(csCase);

        writeAction(caseId, operatorId, "ADMIN", "RESOLVE", resolution, null);
        operationAuditLogService.log(
                operatorId,
                "ADMIN",
                "CS_CASE_RESOLVED",
                "CUSTOMER_SERVICE_CASE",
                String.valueOf(caseId),
                "resolution=" + resolution + ", decision=" + decision
        );
        return ResponseResult.success("工单已处理完成");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> closeCase(Long caseId, Long operatorId, String resolution) {
        CustomerServiceCase csCase = customerServiceCaseMapper.selectById(caseId);
        if (csCase == null) {
            return ResponseResult.error("工单不存在");
        }
        if (csCase.getStatus() == RESOLVED || csCase.getStatus() == top.dlsloveyy.backendtest.constant.CustomerServiceCaseStatus.CLOSED) {
            return ResponseResult.error("工单已结案");
        }

        csCase.setStatus(top.dlsloveyy.backendtest.constant.CustomerServiceCaseStatus.CLOSED);
        csCase.setLatestAction(resolution == null || resolution.isBlank() ? "客服已关闭工单" : resolution.trim());
        csCase.setUpdateTime(LocalDateTime.now());
        csCase.setCloseTime(LocalDateTime.now());
        customerServiceCaseMapper.updateById(csCase);

        writeAction(caseId, operatorId, "ADMIN", "CLOSE", resolution, null);
        operationAuditLogService.log(
                operatorId,
                "ADMIN",
                "CS_CASE_CLOSED",
                "CUSTOMER_SERVICE_CASE",
                String.valueOf(caseId),
                "resolution=" + resolution
        );
        return ResponseResult.success("工单已关闭");
    }

    @Override
    public ResponseResult<List<CustomerServiceCase>> listCases(Integer status, Long assignedAdminId) {
        LambdaQueryWrapper<CustomerServiceCase> query = new LambdaQueryWrapper<>();
        if (status != null) {
            query.eq(CustomerServiceCase::getStatus, status);
        }
        if (assignedAdminId != null) {
            query.eq(CustomerServiceCase::getAssignedAdminId, assignedAdminId);
        }
        query.orderByDesc(CustomerServiceCase::getCreateTime);
        List<CustomerServiceCase> cases = customerServiceCaseMapper.selectList(query);
        fillCaseUsernames(cases);
        return ResponseResult.success(cases);
    }

    @Override
    public ResponseResult<List<CustomerServiceCase>> listMyCases(Long userId, Boolean asSeller) {
        LambdaQueryWrapper<CustomerServiceCase> query = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(asSeller)) {
            query.eq(CustomerServiceCase::getSellerId, userId);
        } else {
            query.eq(CustomerServiceCase::getBuyerId, userId);
        }
        query.orderByDesc(CustomerServiceCase::getCreateTime);
        List<CustomerServiceCase> cases = customerServiceCaseMapper.selectList(query);
        fillCaseUsernames(cases);
        return ResponseResult.success(cases);
    }

    @Override
    public ResponseResult<CustomerServiceCase> getCaseDetail(Long caseId, Long operatorId, Boolean isAdmin) {
        CustomerServiceCase csCase = customerServiceCaseMapper.selectById(caseId);
        if (csCase == null) {
            return ResponseResult.error("工单不存在");
        }
        if (!Boolean.TRUE.equals(isAdmin)
                && !operatorId.equals(csCase.getBuyerId())
                && !operatorId.equals(csCase.getSellerId())) {
            return ResponseResult.error(403, "无权查看该工单");
        }
        return ResponseResult.success(csCase);
    }

    @Override
    public ResponseResult<CustomerServiceCaseFullDetailVO> getCaseFullDetail(Long caseId, Long operatorId, Boolean isAdmin) {
        CustomerServiceCase csCase = customerServiceCaseMapper.selectById(caseId);
        if (csCase == null) {
            return ResponseResult.error("工单不存在");
        }
        if (!Boolean.TRUE.equals(isAdmin)
                && !operatorId.equals(csCase.getBuyerId())
                && !operatorId.equals(csCase.getSellerId())) {
            return ResponseResult.error(403, "无权查看该工单");
        }

        CustomerServiceCaseFullDetailVO detail = new CustomerServiceCaseFullDetailVO();
        detail.setCaseInfo(csCase);

        List<CustomerServiceCaseAction> actions = caseActionMapper.selectList(new LambdaQueryWrapper<CustomerServiceCaseAction>()
                .eq(CustomerServiceCaseAction::getCaseId, caseId)
                .orderByAsc(CustomerServiceCaseAction::getCreateTime));
        detail.setActions(actions);

        if (csCase.getDisputeId() != null && csCase.getDisputeId() > 0) {
            detail.setDispute(tradeDisputeMapper.selectById(csCase.getDisputeId()));
        }

        if (csCase.getOrderId() != null && csCase.getOrderId() > 0) {
            TradeOrder order = tradeOrderMapper.selectById(csCase.getOrderId());
            if (order != null) {
                CustomerServiceCaseFullDetailVO.OrderRefundInfoVO refund = new CustomerServiceCaseFullDetailVO.OrderRefundInfoVO();
                refund.setOrderId(order.getId());
                refund.setOrderNo(order.getOrderNo());
                refund.setOrderStatus(order.getStatus());
                refund.setRefundType(order.getRefundType());
                refund.setRefundStage(order.getRefundStage());
                refund.setRefundRequestedAmount(order.getRefundRequestedAmount());
                refund.setRefundApprovedAmount(order.getRefundApprovedAmount());
                refund.setRefundReason(order.getRefundReason());
                refund.setRefundReasonCode(order.getRefundReasonCode());
                refund.setRefundApplyPacketRaw(order.getRefundApplyPacket());
                refund.setRefundApplyPacketObj(parseRefundPacket(order.getRefundApplyPacket()));
                detail.setOrderRefund(refund);
            }
        }

        detail.setEvidenceTimeline(buildEvidenceTimeline(actions));
        return ResponseResult.success(detail);
    }

    @Override
    public CustomerServiceCase getActiveCaseByDisputeId(Long disputeId) {
        return customerServiceCaseMapper.selectOne(new LambdaQueryWrapper<CustomerServiceCase>()
                .eq(CustomerServiceCase::getDisputeId, disputeId)
                .in(CustomerServiceCase::getStatus, PENDING_ASSIGN, IN_PROGRESS, WAITING_EVIDENCE)
                .last("limit 1"));
    }

    private void writeAction(Long caseId,
                             Long actorId,
                             String actorRole,
                             String actionType,
                             String content,
                             String attachments) {
        CustomerServiceCaseAction action = new CustomerServiceCaseAction();
        action.setCaseId(caseId);
        action.setActorId(actorId);
        action.setActorRole(actorRole);
        action.setActionType(actionType);
        action.setContent(content);
        action.setAttachments(attachments);
        action.setCreateTime(LocalDateTime.now());
        caseActionMapper.insert(action);
    }

    private Map<String, Object> parseRefundPacket(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", raw);
            return fallback;
        }
    }

    private void fillCaseUsernames(List<CustomerServiceCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = new HashMap<>();
        for (CustomerServiceCase csCase : cases) {
            if (csCase == null) {
                continue;
            }
            if (csCase.getBuyerId() != null) {
                userMap.putIfAbsent(csCase.getBuyerId(), null);
            }
            if (csCase.getSellerId() != null && csCase.getSellerId() > 0) {
                userMap.putIfAbsent(csCase.getSellerId(), null);
            }
        }
        if (userMap.isEmpty()) {
            return;
        }
        List<User> users = userMapper.selectBatchIds(userMap.keySet());
        if (users != null) {
            for (User user : users) {
                if (user != null && user.getId() != null) {
                    userMap.put(user.getId(), user);
                }
            }
        }
        for (CustomerServiceCase csCase : cases) {
            if (csCase == null) {
                continue;
            }
            User buyer = userMap.get(csCase.getBuyerId());
            User seller = userMap.get(csCase.getSellerId());
            csCase.setBuyerName(buyer == null ? null : buyer.getUsername());
            csCase.setSellerName(seller == null ? null : seller.getUsername());
        }
    }

    private List<EvidenceTimelineItemVO> buildEvidenceTimeline(List<CustomerServiceCaseAction> actions) {
        List<EvidenceTimelineItemVO> timeline = new java.util.ArrayList<>();
        if (actions == null) {
            return timeline;
        }
        for (CustomerServiceCaseAction action : actions) {
            if (action == null) {
                continue;
            }
            if (!"EVIDENCE_ADDED".equals(action.getActionType()) && !"REQUEST_EVIDENCE".equals(action.getActionType())) {
                continue;
            }
            EvidenceTimelineItemVO item = new EvidenceTimelineItemVO();
            item.setSourceType("CASE_ACTION");
            item.setParty(action.getActorRole());
            item.setActionType(action.getActionType());
            item.setTitle("REQUEST_EVIDENCE".equals(action.getActionType()) ? "催补证" : "补充证据");
            item.setContent(action.getContent());
            if (action.getAttachments() != null && !action.getAttachments().isBlank()) {
                String[] parts = action.getAttachments().split("\\n");
                for (String part : parts) {
                    String trimmed = part == null ? "" : part.trim();
                    if (!trimmed.isEmpty()) {
                        item.getAttachments().add(trimmed);
                    }
                }
            }
            item.setCreateTime(action.getCreateTime() == null ? null : action.getCreateTime().toString());
            timeline.add(item);
        }
        return timeline;
    }

    private BigDecimal resolveEscrowAmount(TradeOrder order) {
        if (order.getSellerIncome() != null && order.getSellerIncome().compareTo(BigDecimal.ZERO) > 0) {
            return order.getSellerIncome();
        }
        if (order.getOrderPrice() != null && order.getOrderPrice().compareTo(BigDecimal.ZERO) > 0) {
            return order.getOrderPrice();
        }
        return BigDecimal.ZERO;
    }
}
