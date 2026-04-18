package top.dlsloveyy.backendtest.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.TradeDispute;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.model.dto.DisputeCreateDTO;
import top.dlsloveyy.backendtest.model.dto.DisputeEscalateDTO;
import top.dlsloveyy.backendtest.model.dto.DisputeEvidenceDTO;
import top.dlsloveyy.backendtest.model.dto.DisputeResolveDTO;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.service.TradeDisputeService;
import top.dlsloveyy.backendtest.util.JwtUtil;

@RestController
@RequestMapping("/api/dispute")
public class TradeDisputeController {

    @Autowired
    private TradeDisputeService tradeDisputeService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    private Long getUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtil.getUserIdFromToken(token.substring(7));
        }
        return null;
    }

    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && Boolean.TRUE.equals(user.getIsAdmin()) && Boolean.TRUE.equals(user.getEnabled());
    }

    @PostMapping("/create")
    public ResponseResult<?> create(@RequestBody DisputeCreateDTO dto, HttpServletRequest request) {
        Long buyerId = getUserId(request);
        if (buyerId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return tradeDisputeService.createDispute(dto.getOrderId(), buyerId, dto.getReason(), dto.getBuyerEvidence());
    }

    @PutMapping("/buyer/evidence")
    public ResponseResult<?> appendBuyerEvidence(@RequestBody DisputeEvidenceDTO dto, HttpServletRequest request) {
        Long buyerId = getUserId(request);
        if (buyerId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return tradeDisputeService.appendBuyerEvidence(dto.getDisputeId(), buyerId, dto.getEvidence());
    }

    @PutMapping("/seller/evidence")
    public ResponseResult<?> appendSellerEvidence(@RequestBody DisputeEvidenceDTO dto, HttpServletRequest request) {
        Long sellerId = getUserId(request);
        if (sellerId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return tradeDisputeService.appendSellerEvidence(dto.getDisputeId(), sellerId, dto.getEvidence());
    }

    @PutMapping("/admin/resolve")
    public ResponseResult<?> resolve(@RequestBody DisputeResolveDTO dto, HttpServletRequest request) {
        Long adminId = getUserId(request);
        if (!isAdmin(adminId)) {
            return ResponseResult.error(403, "无权限");
        }
        return tradeDisputeService.resolveDispute(dto.getDisputeId(), adminId, dto.getDecision(), dto.getResolution());
    }

    @PostMapping("/escalate-after-reject")
    public ResponseResult<?> escalateAfterReject(@RequestBody DisputeEscalateDTO dto, HttpServletRequest request) {
        Long buyerId = getUserId(request);
        if (buyerId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return tradeDisputeService.escalateAfterReject(
                dto.getOrderId(),
                buyerId,
                dto.getReason(),
                dto.getBuyerEvidence(),
                dto.getPriority()
        );
    }

    @GetMapping("/my")
    public ResponseResult<?> myDisputes(@RequestParam(defaultValue = "false") boolean asSeller,
                                        HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return tradeDisputeService.listMyDisputes(userId, asSeller);
    }

    @GetMapping("/detail/{id}")
    public ResponseResult<?> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        TradeDispute dispute = tradeDisputeService.getById(id);
        if (dispute == null) {
            return ResponseResult.error("争议单不存在");
        }
        if (!isAdmin(userId) && !userId.equals(dispute.getBuyerId()) && !userId.equals(dispute.getSellerId())) {
            return ResponseResult.error(403, "无权查看");
        }
        return ResponseResult.success(dispute);
    }
}
