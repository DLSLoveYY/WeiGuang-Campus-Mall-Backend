package top.dlsloveyy.backendtest.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.security.Principal;
import java.util.Collections;

/**
 * WebSocket 鉴权拦截器：在 STOMP CONNECT 时解析 JWT，设置用户 Principal。
 * Principal.getName() 返回 userId 字符串，供 /user/{userId}/queue/messages 路由使用。
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                try {
                    Claims claims = jwtUtil.parseClaimsUnsafe(token);
                    String username = claims.getSubject();
                    if (username != null) {
                        User user = userMapper.selectOne(
                                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
                        if (user != null) {
                            // Principal name = userId，用于点对点路由
                            final Long userId = user.getId();
                            Principal principal = () -> String.valueOf(userId);
                            accessor.setUser(new UsernamePasswordAuthenticationToken(
                                    principal, null, Collections.emptyList()));
                        }
                    }
                } catch (JwtException ignored) {
                    // token 无效或过期：不设置 Principal，连接仍允许（业务层判断）
                }
            }
        }
        return message;
    }
}
