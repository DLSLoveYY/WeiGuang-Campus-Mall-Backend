package top.dlsloveyy.backendtest.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.dlsloveyy.backendtest.entity.TradeDispute;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;

import java.util.List;

public interface TradeDisputeService extends IService<TradeDispute> {

    ResponseResult<?> createDispute(Long orderId, Long buyerId, String reason, String buyerEvidence);

    ResponseResult<?> appendBuyerEvidence(Long disputeId, Long buyerId, String evidence);

    ResponseResult<?> appendSellerEvidence(Long disputeId, Long sellerId, String evidence);

    ResponseResult<?> resolveDispute(Long disputeId, Long adminId, Integer decision, String resolution);

    ResponseResult<?> escalateAfterReject(Long orderId,
                                          Long buyerId,
                                          String reason,
                                          String buyerEvidence,
                                          Integer priority);

    ResponseResult<List<TradeDispute>> listMyDisputes(Long userId, Boolean asSeller);
}
