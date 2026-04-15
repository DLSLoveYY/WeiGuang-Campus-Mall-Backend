package top.dlsloveyy.backendtest.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private static final long EXPIRATION_TIME = 86400000; // 1 天

    @Value("${jwt.secret}")
    private String secret;

    private Key key;

    @PostConstruct
    public void init() {
        System.out.println("【JWT init】secret = " + secret);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 【旧版兼容】生成 JWT token (只含 username)
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key)
                .compact();
    }

    /**
     * 【新增方法】生成 JWT token (同时包含 userId 和 username)
     * 建议你的登录接口改用这个方法来生成 Token！
     */
    public String generateToken(Long userId, String username) {
        return Jwts.builder()
                .claim("userId", userId) // 将 userId 放入自定义的 Payload (载荷) 中
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key)
                .compact();
    }

    /**
     * 从 token 中获取用户名
     */
    public String getUsernameFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * 【新增方法】从 token 中提取 userId (供订单模块使用)
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // 从自定义载荷中取出 userId
            Object userIdObj = claims.get("userId");
            if (userIdObj != null) {
                // 转换类型，防止 Integer 和 Long 强转报错
                return Long.valueOf(userIdObj.toString());
            }
            return null;
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * 兼容 controller 中 extractUsername(token) 调用
     * 支持自动去除 Bearer 前缀
     */
    public String extractUsername(String token) {
        if (token != null && token.toLowerCase().startsWith("bearer ")) {
            token = token.substring(7);
        }
        return getUsernameFromToken(token);
    }
}