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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
                Claims claims = jwtUtil.parseClaimsUnsafe(token);
                String username = claims.getSubject();

                if (username != null) {
                    User user = userMapper.selectOne(
                            new LambdaQueryWrapper<User>().eq(User::getUsername, username));
                    if (user != null) {
                        currentUser.set(user);

                        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                        if (Boolean.TRUE.equals(user.getIsAdmin())) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                        }

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (ExpiredJwtException ex) {
                Claims claims = ex.getClaims();
                if (claims != null) {
                    Object tokenType = claims.get("tokenType");
                    if ("access".equals(tokenType)) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":401,\"message\":\"AccessToken已过期，请刷新\"}");
                        return;
                    }
                }
            } catch (JwtException e) {
                // token 无效时放行给后续鉴权处理
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            currentUser.remove();
            SecurityContextHolder.clearContext();
        }
    }
}
