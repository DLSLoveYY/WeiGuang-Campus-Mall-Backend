package top.dlsloveyy.backendtest.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserMapper userMapper;

    /**
     * 用 RefreshToken 换取新的 AccessToken
     * 请求体：{"refreshToken": "..."}
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "refreshToken不能为空"));
        }

        // 1. 验签 + 校验 tokenType，过期或非法返回 null
        Long userId = jwtUtil.getUserIdFromRefreshToken(refreshToken);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "refreshToken无效或已过期"));
        }

        // 2. 查 Redis，防止已登出的 refreshToken 被复用
        String redisKey = "refresh_token:" + userId;
        Object storedToken = redisTemplate.opsForValue().get(redisKey);
        if (storedToken == null || !storedToken.toString().equals(refreshToken)) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "refreshToken已失效，请重新登录"));
        }

        // 3. 查库获取最新用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "用户不存在"));
        }

        // 4. 签发新 AccessToken
        String newAccessToken = jwtUtil.generateAccessToken(userId, user.getUsername());

        // 5. 滑动续期：刷新 RefreshToken 在 Redis 中的 TTL
        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "token", newAccessToken,        // 旧字段兼容
                "accessToken", newAccessToken
        ));
    }
}
