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

    private static final long EXPIRATION_TIME = 86400000;           // 旧版兼容：1 天
    private static final long ACCESS_TOKEN_EXPIRY  = 15 * 60 * 1000L;            // 15 分钟
    private static final long REFRESH_TOKEN_EXPIRY = 7 * 24 * 60 * 60 * 1000L;  // 7 天

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

    // ==================== 双Token 鉴权方法 ====================

    /**
     * 生成 AccessToken（15分钟），携带 tokenType=access 标识
     */
    public String generateAccessToken(Long userId, String username) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("tokenType", "access")
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
                .signWith(key)
                .compact();
    }

    /**
     * 生成 RefreshToken（7天），携带 tokenType=refresh 标识
     */
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("tokenType", "refresh")
                .setSubject(String.valueOf(userId))
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY))
                .signWith(key)
                .compact();
    }

    /**
     * 从 RefreshToken 中提取 userId，同时校验 tokenType
     * 若 token 无效、过期或 tokenType 不是 refresh，返回 null
     */
    public Long getUserIdFromRefreshToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            Object tokenType = claims.get("tokenType");
            if (!"refresh".equals(tokenType)) {
                return null;
            }
            Object userIdObj = claims.get("userId");
            return userIdObj != null ? Long.valueOf(userIdObj.toString()) : null;
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * 解析 token 并返回 Claims，会抛出 JwtException（含 ExpiredJwtException）
     * 供 JwtFilter 捕获 ExpiredJwtException 并区分 token 类型
     */
    public Claims parseClaimsUnsafe(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}