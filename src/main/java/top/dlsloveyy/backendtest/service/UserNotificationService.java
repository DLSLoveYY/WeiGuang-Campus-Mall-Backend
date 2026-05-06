package top.dlsloveyy.backendtest.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.dlsloveyy.backendtest.entity.UserNotification;

import java.util.List;

public interface UserNotificationService extends IService<UserNotification> {

    UserNotification notifyUser(Long recipientId,
                                String type,
                                String title,
                                String content,
                                String relatedType,
                                Long relatedId);

    int notifyEnabledUsers(String type,
                           String title,
                           String content,
                           String relatedType,
                           Long relatedId);

    long countUnread(Long userId);

    List<UserNotification> listMyNotifications(Long userId, Boolean unreadOnly, Integer page, Integer size);

    boolean markRead(Long notificationId, Long userId);

    int markAllRead(Long userId);
}
