package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.config.JwtFilter;
import top.dlsloveyy.backendtest.entity.ChatMessage;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.ChatMessageMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * STOMP 消息处理：客户端 send 到 /app/chat.send
     * payload 格式：{ receiverId: Long, content: String }
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) return;

        Long senderId = resolveSenderId(principal);
        if (senderId == null) {
            return;
        }

        Object receiverIdObj = payload.get("receiverId");
        if (receiverIdObj == null) {
            return;
        }

        Long receiverId;
        try {
            receiverId = Long.valueOf(receiverIdObj.toString());
        } catch (NumberFormatException e) {
            return;
        }

        String content = payload.getOrDefault("content", "").toString().trim();
        if (content.isEmpty()) return;

        // 持久化到数据库
        ChatMessage msg = new ChatMessage();
        msg.setSenderId(senderId);
        msg.setReceiverId(receiverId);
        msg.setContent(content);
        msg.setIsRead(false);
        msg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(msg);

        // 组装推送 payload（含发送者信息，方便前端直接渲染）
        User sender = userMapper.selectById(senderId);
        Map<String, Object> pushData = new HashMap<>();
        pushData.put("id", msg.getId());
        pushData.put("senderId", senderId);
        pushData.put("receiverId", receiverId);
        pushData.put("content", content);
        pushData.put("createTime", msg.getCreateTime());
        pushData.put("senderName", sender != null ? sender.getUsername() : "");
        pushData.put("senderAvatar", sender != null && sender.getAvatar() != null ? sender.getAvatar() : "");

        // 推送给接收方：/user/{receiverId}/queue/messages
        messagingTemplate.convertAndSendToUser(String.valueOf(receiverId), "/queue/messages", pushData);
        // 同时推送给发送方（让自己的界面也实时显示）
        messagingTemplate.convertAndSendToUser(String.valueOf(senderId), "/queue/messages", pushData);
    }

    private Long resolveSenderId(Principal principal) {
        if (principal == null) {
            return null;
        }

        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException ignored) {
            User currentUser = JwtFilter.currentUser.get();
            return currentUser != null ? currentUser.getId() : null;
        }
    }

    /**
     * REST 接口：获取与某用户的历史消息（双向查询，按时间正序）
     */
    @GetMapping("/api/chat/history/{otherUserId}")
    public ResponseEntity<?> getChatHistory(@PathVariable Long otherUserId) {
        User currentUser = JwtFilter.currentUser.get();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "请先登录"));
        }
        Long myId = currentUser.getId();

        // 查询双向消息
        List<ChatMessage> msgs = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .and(w -> w.eq(ChatMessage::getSenderId, myId).eq(ChatMessage::getReceiverId, otherUserId))
                        .or(w -> w.eq(ChatMessage::getSenderId, otherUserId).eq(ChatMessage::getReceiverId, myId))
                        .orderByAsc(ChatMessage::getCreateTime)
        );

        // 标记已读（对方发的未读消息）
        chatMessageMapper.update(null,
                new LambdaUpdateWrapper<ChatMessage>()
                        .eq(ChatMessage::getSenderId, otherUserId)
                        .eq(ChatMessage::getReceiverId, myId)
                        .eq(ChatMessage::getIsRead, false)
                        .set(ChatMessage::getIsRead, true)
        );

        return ResponseEntity.ok(Map.of("code", 200, "data", msgs));
    }

    /**
     * REST 接口：获取会话列表（我与哪些用户有过对话，每个会话显示最后一条消息）
     */
    @GetMapping("/api/chat/sessions")
    public ResponseEntity<?> getChatSessions() {
        User currentUser = JwtFilter.currentUser.get();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "请先登录"));
        }
        Long myId = currentUser.getId();

        // 查询所有与我相关的消息
        List<ChatMessage> all = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .and(w -> w.eq(ChatMessage::getSenderId, myId).or().eq(ChatMessage::getReceiverId, myId))
                        .orderByDesc(ChatMessage::getCreateTime)
        );

        // 按对话对象去重，取每个对话的最新消息
        Map<Long, ChatMessage> lastMsgMap = new LinkedHashMap<>();
        for (ChatMessage m : all) {
            Long otherId = m.getSenderId().equals(myId) ? m.getReceiverId() : m.getSenderId();
            lastMsgMap.putIfAbsent(otherId, m);
        }

        // 批量查对话对象的用户信息
        if (lastMsgMap.isEmpty()) {
            return ResponseEntity.ok(Map.of("code", 200, "data", List.of()));
        }
        List<Long> otherIds = new ArrayList<>(lastMsgMap.keySet());
        Map<Long, User> userMap = userMapper.selectBatchIds(otherIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        // 统计每个会话的未读数
        List<Map<String, Object>> result = lastMsgMap.entrySet().stream().map(entry -> {
            Long otherId = entry.getKey();
            ChatMessage lastMsg = entry.getValue();
            User other = userMap.get(otherId);

            long unread = all.stream()
                    .filter(m -> m.getSenderId().equals(otherId) && m.getReceiverId().equals(myId) && !m.getIsRead())
                    .count();

            Map<String, Object> session = new HashMap<>();
            session.put("userId", otherId);
            session.put("username", other != null ? other.getUsername() : "未知用户");
            session.put("avatar", other != null && other.getAvatar() != null ? other.getAvatar() : "");
            session.put("lastMessage", lastMsg.getContent());
            session.put("lastTime", lastMsg.getCreateTime());
            session.put("unreadCount", unread);
            return session;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("code", 200, "data", result));
    }

    /**
     * REST 接口：获取当前用户的未读消息总数
     */
    @GetMapping("/api/chat/unread-count")
    public ResponseEntity<?> getUnreadCount() {
        User currentUser = JwtFilter.currentUser.get();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "请先登录"));
        }
        long count = chatMessageMapper.selectCount(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getReceiverId, currentUser.getId())
                        .eq(ChatMessage::getIsRead, false)
        );
        return ResponseEntity.ok(Map.of("code", 200, "data", count));
    }
}
