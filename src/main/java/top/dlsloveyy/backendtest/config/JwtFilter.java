package top.dlsloveyy.backendtest.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
public class JwtFilter extends OncePerRequestFilter { // ✅ 改用 OncePerRequestFilter

    @Autowired
    private UserMapper userMapper; // ✅ 直接注入，不要在 init 里手动获取

    @Autowired
    private JwtUtil jwtUtil;

    // ThreadLocal 依然保留，这在 Service 层获取当前用户非常方便
    public static final ThreadLocal<User> currentUser = new ThreadLocal<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            // 1. 从 Token 中解析出用户名
            String username = jwtUtil.getUsernameFromToken(token);

            if (username != null) {
                // 2. 查库获取完整用户信息
                User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
                if (user != null) {
                    // 3. 存入 ThreadLocal
                    currentUser.set(user);
                }
            }
        }

        try {
            // 继续执行后面的过滤器或 Controller
            filterChain.doFilter(request, response);
        } finally {
            // ⚠️ 极其重要：请求结束后必须清理 ThreadLocal，防止内存泄漏和数据污染
            currentUser.remove();
        }
    }
}