package top.dlsloveyy.backendtest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.dlsloveyy.backendtest.entity.TradeDispute;
import top.dlsloveyy.backendtest.entity.TradeOrder;
import top.dlsloveyy.backendtest.mapper.TradeDisputeMapper;
import top.dlsloveyy.backendtest.mapper.TradeOrderMapper;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.service.AccountService;
import top.dlsloveyy.backendtest.service.CustomerServiceCaseService;
import top.dlsloveyy.backendtest.service.OperationAuditLogService;
import top.dlsloveyy.backendtest.service.TradeDisputeService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static top.dlsloveyy.backendtest.constant.DisputeStatus.BUYER_WON;
import static top.dlsloveyy.backendtest.constant.DisputeStatus.CLOSED;
import static top.dlsloveyy.backendtest.constant.DisputeStatus.IN_REVIEW;
import static top.dlsloveyy.backendtest.constant.DisputeStatus.OPEN;
import static top.dlsloveyy.backendtest.constant.DisputeStatus.SELLER_WON;
import static top.dlsloveyy.backendtest.constant.OrderStatus.REFUND_REQUESTED;
import static top.dlsloveyy.backendtest.constant.OrderStatus.REFUNDED;
import static top.dlsloveyy.backendtest.constant.OrderStatus.SHIPPED_PENDING_RECEIPT;

@Service
public class TradeDisputeServiceImpl extends ServiceImpl<TradeDisputeMapper, TradeDispute> implements TradeDisputeService {

    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    @Autowired
    private AccountService accountService;

    @Autowired
    private OperationAuditLogService operationAuditLogService;

    @Autowired
    private CustomerServiceCaseService customerServiceCaseService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createDispute(Long orderId, Long buyerId, String reason, String buyerEvidence) {
        TradeOrder order = tradeOrderMapper.selectById(orderId);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            return ResponseResult.error("订单不存在或无权操作");
        }
        if (order.getStatus() != REFUND_REQUESTED && order.getStatus() != SHIPPED_PENDING_RECEIPT) {
            return ResponseResult.error("当前订单状态不支持发起争议");
        }

        TradeDispute existed = this.getOne(new LambdaQueryWrapper<TradeDispute>()
                .eq(TradeDispute::getOrderId, orderId)
                .in(TradeDispute::getStatus, OPEN, IN_REVIEW));
        if (existed != null) {
            return ResponseResult.error("该订单已有处理中争议单");
        }

        TradeDispute dispute = new TradeDispute();
        dispute.setOrderId(orderId);
        dispute.setBuyerId(buyerId);
        dispute.setSellerId(order.getSellerId());
        dispute.setReason(reason);
        dispute.setBuyerEvidence(buyerEvidence);
        dispute.setStatus(OPEN);
        dispute.setCreateTime(LocalDateTime.now());
        dispute.setUpdateTime(LocalDateTime.now());
        dispute.setDeadlineTime(LocalDateTime.now().plusDays(3));
        this.save(dispute);

        if (order.getStatus() != REFUND_REQUESTED) {
            order.setStatus(REFUND_REQUESTED);
            order.setUpdateTime(LocalDateTime.now());
            tradeOrderMapper.updateById(order);
        }

        operationAuditLogService.log(
                buyerId,
                "USER",
                "DISPUTE_CREATED",
                "TRADE_DISPUTE",
                String.valueOf(dispute.getId()),
                "orderId=" + orderId + ", status=" + OPEN
        );
        return ResponseResult.success("争议单已创建", dispute.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> appendBuyerEvidence(Long disputeId, Long buyerId, String evidence) {
        TradeDispute dispute = this.getById(disputeId);
        if (dispute == null || !dispute.getBuyerId().equals(buyerId)) {
            return ResponseResult.error("争议单不存在或无权操作");
        }
        if (dispute.getStatus() == BUYER_WON || dispute.getStatus() == SELLER_WON || dispute.getStatus() == CLOSED) {
            return ResponseResult.error("争议单已结束，无法补充证据");
        }

        String previous = dispute.getBuyerEvidence() == null ? "" : dispute.getBuyerEvidence() + "\n";
        dispute.setBuyerEvidence(previous + evidence);
        dispute.setStatus(IN_REVIEW);
        dispute.setUpdateTime(LocalDateTime.now());
        this.updateById(dispute);

        operationAuditLogService.log(
                buyerId,
                "USER",
                "DISPUTE_BUYER_EVIDENCE_ADDED",
                "TRADE_DISPUTE",
                String.valueOf(disputeId),
                "status=" + IN_REVIEW
        );

        return ResponseResult.success("已补充买家证据");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> appendSellerEvidence(Long disputeId, Long sellerId, String evidence) {
        TradeDispute dispute = this.getById(disputeId);
        if (dispute == null || !dispute.getSellerId().equals(sellerId)) {
            return ResponseResult.error("争议单不存在或无权操作");
        }
        if (dispute.getStatus() == BUYER_WON || dispute.getStatus() == SELLER_WON || dispute.getStatus() == CLOSED) {
            return ResponseResult.error("争议单已结束，无法补充证据");
        }

        String previous = dispute.getSellerEvidence() == null ? "" : dispute.getSellerEvidence() + "\n";
        dispute.setSellerEvidence(previous + evidence);
        dispute.setStatus(IN_REVIEW);
        dispute.setUpdateTime(LocalDateTime.now());
        this.updateById(dispute);

        operationAuditLogService.log(
                sellerId,
                "USER",
                "DISPUTE_SELLER_EVIDENCE_ADDED",
                "TRADE_DISPUTE",
                String.valueOf(disputeId),
                "status=" + IN_REVIEW
        );

        return ResponseResult.success("已补充卖家证据");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> resolveDispute(Long disputeId, Long adminId, Integer decision, String resolution) {
        TradeDispute dispute = this.getById(disputeId);
        if (dispute == null) {
            return ResponseResult.error("争议单不存在");
        }
        if (dispute.getStatus() == BUYER_WON || dispute.getStatus() == SELLER_WON || dispute.getStatus() == CLOSED) {
            return ResponseResult.error("争议单已处理完成");
        }
        if (decision == null || (decision != BUYER_WON && decision != SELLER_WON)) {
            return ResponseResult.error("无效仲裁决策");
        }

        TradeOrder order = tradeOrderMapper.selectById(dispute.getOrderId());
        if (order == null) {
            return ResponseResult.error("关联订单不存在");
        }

        dispute.setStatus(decision);
        dispute.setResolution(resolution);
        dispute.setProcessorId(adminId);
        dispute.setUpdateTime(LocalDateTime.now());
        dispute.setCloseTime(LocalDateTime.now());
        this.updateById(dispute);

        if (decision == BUYER_WON) {
            order.setStatus(REFUNDED);
            order.setUpdateTime(LocalDateTime.now());
            tradeOrderMapper.updateById(order);

            BigDecimal escrowAmount = resolveEscrowAmount(order);
            if (escrowAmount.compareTo(BigDecimal.ZERO) > 0) {
                accountService.unfreeze(
                        order.getSellerId(),
                        escrowAmount,
                        "DISPUTE_REFUND_UNFREEZE",
                        String.valueOf(order.getId()),
                        "DISPUTE_REFUND_UNFREEZE:" + order.getId() + ":" + order.getSellerId(),
                        "争议裁决退款，释放卖家待结算金额"
                );
            }

            accountService.credit(
                    order.getBuyerId(),
                    order.getOrderPrice(),
                    "DISPUTE_REFUND",
                    String.valueOf(order.getId()),
                    "DISPUTE_REFUND:IN:" + order.getId() + ":" + order.getBuyerId(),
                    "争议裁决买家胜，退款入账"
            );
        } else {
            order.setStatus(SHIPPED_PENDING_RECEIPT);
            order.setUpdateTime(LocalDateTime.now());
            tradeOrderMapper.updateById(order);
        }

        operationAuditLogService.log(
                adminId,
                "ADMIN",
                "DISPUTE_RESOLVED",
                "TRADE_DISPUTE",
                String.valueOf(disputeId),
                "decision=" + decision + ", orderStatus=" + order.getStatus()
        );

        return ResponseResult.success("争议单处理完成");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> escalateAfterReject(Long orderId,
                                                 Long buyerId,
                                                 String reason,
                                                 String buyerEvidence,
                                                 Integer priority) {
        TradeOrder order = tradeOrderMapper.selectById(orderId);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            return ResponseResult.error("订单不存在或无权操作");
        }
        if (order.getStatus() != SHIPPED_PENDING_RECEIPT) {
            return ResponseResult.error("当前订单状态不支持客服介入");
        }

        TradeDispute latestDispute = this.getOne(new LambdaQueryWrapper<TradeDispute>()
                .eq(TradeDispute::getOrderId, orderId)
                .orderByDesc(TradeDispute::getCreateTime)
                .last("limit 1"));
        if (latestDispute == null) {
            return ResponseResult.error("请先发起退款申请后再申请客服介入");
        }
        if (!buyerId.equals(latestDispute.getBuyerId())) {
            return ResponseResult.error("无权操作该争议单");
        }

        if (latestDispute.getStatus() == OPEN || latestDispute.getStatus() == IN_REVIEW) {
            latestDispute.setStatus(CLOSED);
            latestDispute.setResolution("SELLER_REJECTED_REFUND");
            latestDispute.setUpdateTime(LocalDateTime.now());
            latestDispute.setCloseTime(LocalDateTime.now());
            this.updateById(latestDispute);
        }

        TradeDispute newDispute = new TradeDispute();
        newDispute.setOrderId(orderId);
        newDispute.setBuyerId(buyerId);
        newDispute.setSellerId(order.getSellerId());
        newDispute.setReason(reason == null ? "卖家拒绝后申请客服介入" : reason);
        newDispute.setBuyerEvidence(buyerEvidence);
        newDispute.setStatus(IN_REVIEW);
        newDispute.setCreateTime(LocalDateTime.now());
        newDispute.setUpdateTime(LocalDateTime.now());
        newDispute.setDeadlineTime(LocalDateTime.now().plusDays(3));
        this.save(newDispute);

        order.setStatus(REFUND_REQUESTED);
        order.setUpdateTime(LocalDateTime.now());
        tradeOrderMapper.updateById(order);

        ResponseResult<?> caseResult = customerServiceCaseService.createCase(
                orderId,
                newDispute.getId(),
                buyerId,
                order.getSellerId(),
                "REFUND_REAPPLY_AFTER_REJECT",
                "买家在卖家拒绝后申请客服介入",
                priority
        );

        operationAuditLogService.log(
                buyerId,
                "USER",
                "DISPUTE_ESCALATED_TO_CS",
                "TRADE_DISPUTE",
                String.valueOf(newDispute.getId()),
                "orderId=" + orderId + ", orderStatus=" + REFUND_REQUESTED
        );

        return ResponseResult.success("客服已介入处理", caseResult.getData());
    }

    @Override
    public ResponseResult<List<TradeDispute>> listMyDisputes(Long userId, Boolean asSeller) {
        LambdaQueryWrapper<TradeDispute> query = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(asSeller)) {
            query.eq(TradeDispute::getSellerId, userId);
        } else {
            query.eq(TradeDispute::getBuyerId, userId);
        }
        query.orderByDesc(TradeDispute::getCreateTime);
        return ResponseResult.success(this.list(query));
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
