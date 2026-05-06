package top.dlsloveyy.backendtest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.entity.UserNotification;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.mapper.UserNotificationMapper;
import top.dlsloveyy.backendtest.service.UserNotificationService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class UserNotificationServiceImpl extends ServiceImpl<UserNotificationMapper, UserNotification> implements UserNotificationService {

    @Autowired
    private UserNotificationMapper userNotificationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public UserNotification notifyUser(Long recipientId,
                                       String type,
                                       String title,
                                       String content,
                                       String relatedType,
                                       Long relatedId) {
        if (recipientId == null) {
            return null;
        }
        UserNotification notification = new UserNotification();
        notification.setRecipientId(recipientId);
        notification.setType(type == null ? "SYSTEM" : type);
        notification.setTitle(title == null ? "系统通知" : title);
        notification.setContent(content == null ? "" : content);
        notification.setRelatedType(relatedType);
        notification.setRelatedId(relatedId);
        notification.setChannel("IN_APP");
        notification.setIsRead(false);
        notification.setCreateTime(LocalDateTime.now());
        notification.setUpdateTime(LocalDateTime.now());
        userNotificationMapper.insert(notification);

        Map<String, Object> push = new LinkedHashMap<>();
        push.put("id", notification.getId());
        push.put("type", notification.getType());
        push.put("title", notification.getTitle());
        push.put("content", notification.getContent());
        push.put("relatedType", notification.getRelatedType());
        push.put("relatedId", notification.getRelatedId());
        push.put("createTime", notification.getCreateTime());
        push.put("isRead", notification.getIsRead());
        messagingTemplate.convertAndSendToUser(String.valueOf(recipientId), "/queue/notifications", push);
        return notification;
    }

    @Override
    public int notifyEnabledUsers(String type,
                                  String title,
                                  String content,
                                  String relatedType,
                                  Long relatedId) {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getEnabled, true));
        int count = 0;
        for (User user : users) {
            if (user != null && user.getId() != null) {
                notifyUser(user.getId(), type, title, content, relatedType, relatedId);
                count++;
            }
        }
        return count;
    }

    @Override
    public long countUnread(Long userId) {
        return userNotificationMapper.selectCount(new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getRecipientId, userId)
                .eq(UserNotification::getIsRead, false));
    }

    @Override
    public List<UserNotification> listMyNotifications(Long userId, Boolean unreadOnly, Integer page, Integer size) {
        List<UserNotification> all = userNotificationMapper.selectList(new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getRecipientId, userId)
                .orderByDesc(UserNotification::getCreateTime));
        if (Boolean.TRUE.equals(unreadOnly)) {
            all = all.stream().filter(n -> Boolean.FALSE.equals(n.getIsRead())).toList();
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new ArrayList<>(all.subList(from, to));
    }

    @Override
    public boolean markRead(Long notificationId, Long userId) {
        if (notificationId == null || userId == null) {
            return false;
        }
        return this.lambdaUpdate()
                .eq(UserNotification::getId, notificationId)
                .eq(UserNotification::getRecipientId, userId)
                .eq(UserNotification::getIsRead, false)
                .set(UserNotification::getIsRead, true)
                .set(UserNotification::getReadTime, LocalDateTime.now())
                .set(UserNotification::getUpdateTime, LocalDateTime.now())
                .update();
    }

    @Override
    public int markAllRead(Long userId) {
        if (userId == null) {
            return 0;
        }
        return userNotificationMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserNotification>()
                        .eq(UserNotification::getRecipientId, userId)
                        .eq(UserNotification::getIsRead, false)
                        .set(UserNotification::getIsRead, true)
                        .set(UserNotification::getReadTime, LocalDateTime.now())
                        .set(UserNotification::getUpdateTime, LocalDateTime.now()));
    }
}
