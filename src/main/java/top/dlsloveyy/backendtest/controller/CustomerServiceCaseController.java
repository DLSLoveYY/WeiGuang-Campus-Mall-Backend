package top.dlsloveyy.backendtest.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.CustomerServiceCase;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.service.CustomerServiceCaseService;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.util.Map;

@RestController
@RequestMapping("/api/cs/case")
public class CustomerServiceCaseController {

    @Autowired
    private CustomerServiceCaseService customerServiceCaseService;

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

    @GetMapping("/my")
    public ResponseResult<?> listMyCases(@RequestParam(defaultValue = "false") boolean asSeller,
                                         HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return customerServiceCaseService.listMyCases(userId, asSeller);
    }

    @GetMapping("/{id}")
    public ResponseResult<?> caseDetail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return customerServiceCaseService.getCaseDetail(id, userId, isAdmin(userId));
    }

    @GetMapping("/{id}/full-detail")
    public ResponseResult<?> caseFullDetail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return customerServiceCaseService.getCaseFullDetail(id, userId, isAdmin(userId));
    }

    @GetMapping("/{id}/actions")
    public ResponseResult<?> caseActions(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return customerServiceCaseService.listCaseActions(id, userId, isAdmin(userId));
    }

    @PostMapping("/{id}/action")
    public ResponseResult<?> appendAction(@PathVariable Long id,
                                          @RequestBody Map<String, String> payload,
                                          HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }

        String actionType = payload.getOrDefault("actionType", "COMMENT");
        String content = payload.get("content");
        String attachments = payload.get("attachments");

        String actorRole = isAdmin(userId) ? "ADMIN" : "USER";
        return customerServiceCaseService.appendCaseAction(id, userId, actorRole, actionType, content, attachments);
    }

    @PostMapping("/request")
    public ResponseResult<?> createUserRequest(@RequestBody Map<String, String> payload,
                                               HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        String category = payload.getOrDefault("category", "其他");
        String title = payload.get("title");
        String detail = payload.get("detail");
        String attachments = payload.get("attachments");
        Long orderId = null;
        if (payload.get("orderId") != null && !payload.get("orderId").isBlank()) {
            try {
                orderId = Long.valueOf(payload.get("orderId"));
            } catch (NumberFormatException ex) {
                return ResponseResult.error("订单ID格式不正确");
            }
        }
        return customerServiceCaseService.createUserRequest(userId, orderId, category, title, detail, attachments, 2);
    }

    @GetMapping("/admin/list")
    public ResponseResult<?> adminList(@RequestParam(required = false) Integer status,
                                       @RequestParam(required = false) Long assignedAdminId,
                                       HttpServletRequest request) {
        Long userId = getUserId(request);
        if (!isAdmin(userId)) {
            return ResponseResult.error(403, "无权限");
        }
        return customerServiceCaseService.listCases(status, assignedAdminId);
    }

    @PutMapping("/admin/{id}/assign")
    public ResponseResult<?> assign(@PathVariable Long id,
                                    @RequestBody Map<String, Long> payload,
                                    HttpServletRequest request) {
        Long operatorId = getUserId(request);
        if (!isAdmin(operatorId)) {
            return ResponseResult.error(403, "无权限");
        }
        Long adminId = payload.get("adminId");
        if (adminId == null) {
            adminId = operatorId;
        }
        return customerServiceCaseService.assignCase(id, adminId, operatorId);
    }

    @PutMapping("/admin/{id}/resolve")
    public ResponseResult<?> resolve(@PathVariable Long id,
                                     @RequestBody Map<String, Object> payload,
                                     HttpServletRequest request) {
        Long operatorId = getUserId(request);
        if (!isAdmin(operatorId)) {
            return ResponseResult.error(403, "无权限");
        }
        Integer decision = payload.get("decision") == null ? null : Integer.valueOf(String.valueOf(payload.get("decision")));
        String resolution = payload.get("resolution") == null ? "客服已处理" : String.valueOf(payload.get("resolution"));
        return customerServiceCaseService.resolveCase(id, operatorId, decision, resolution);
    }

    @PutMapping("/admin/{id}/close")
    public ResponseResult<?> close(@PathVariable Long id,
                                   @RequestBody Map<String, Object> payload,
                                   HttpServletRequest request) {
        Long operatorId = getUserId(request);
        if (!isAdmin(operatorId)) {
            return ResponseResult.error(403, "无权限");
        }
        String resolution = payload.get("resolution") == null ? "客服已关闭工单" : String.valueOf(payload.get("resolution"));
        return customerServiceCaseService.closeCase(id, operatorId, resolution);
    }
}
