package top.dlsloveyy.backendtest.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    // ThreadLocal 供 Service 层获取当前用户
    public static final ThreadLocal<User> currentUser = new ThreadLocal<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                // 使用 parseClaimsUnsafe 解析，会在过期时抛出 ExpiredJwtException
                Claims claims = jwtUtil.parseClaimsUnsafe(token);
                String username = claims.getSubject();

                if (username != null) {
                    User user = userMapper.selectOne(
                            new LambdaQueryWrapper<User>().eq(User::getUsername, username));
                    if (user != null) {
                        currentUser.set(user);
                    }
                }
            } catch (ExpiredJwtException ex) {
                // 仅对新式 AccessToken（含 tokenType=access）返回 401，触发前端刷新
                // 旧式 admin token（无 tokenType 字段）静默通过，不干扰管理端
                Object tokenType = ex.getClaims().get("tokenType");
                if ("access".equals(tokenType)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"AccessToken已过期，请刷新\"}");
                    return;
                }
                // 旧式 token 过期：静默通过，ThreadLocal 中无用户
            } catch (JwtException e) {
                // token 签名非法等：静默通过
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后必须清理 ThreadLocal，防止内存泄漏和数据污染
            currentUser.remove();
        }
    }
}
