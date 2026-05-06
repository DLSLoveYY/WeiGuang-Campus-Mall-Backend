package top.dlsloveyy.backendtest.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.config.JwtFilter;
import top.dlsloveyy.backendtest.entity.UserNotification;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.service.UserNotificationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private UserNotificationService userNotificationService;

    private Long resolveUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        var current = JwtFilter.currentUser.get();
        return current == null ? null : current.getId();
    }

    @GetMapping("/my")
    public ResponseResult<?> myNotifications(@RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "10") Integer size,
                                             @RequestParam(defaultValue = "false") Boolean unreadOnly,
                                             HttpServletRequest request) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        List<UserNotification> list = userNotificationService.listMyNotifications(userId, unreadOnly, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", userNotificationService.countUnread(userId));
        data.put("unreadCount", userNotificationService.countUnread(userId));
        return ResponseResult.success(data);
    }

    @GetMapping("/unread-count")
    public ResponseResult<?> unreadCount(HttpServletRequest request) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return ResponseResult.success(userNotificationService.countUnread(userId));
    }

    @PutMapping("/{id}/read")
    public ResponseResult<?> markRead(@PathVariable Long id, HttpServletRequest request) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        boolean ok = userNotificationService.markRead(id, userId);
        return ok ? ResponseResult.success("已标记已读") : ResponseResult.error("通知不存在或无权操作");
    }

    @PutMapping("/read-all")
    public ResponseResult<?> markAllRead(HttpServletRequest request) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        int count = userNotificationService.markAllRead(userId);
        return ResponseResult.success("已全部标记已读", count);
    }
}
