package top.dlsloveyy.backendtest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.dlsloveyy.backendtest.annotation.RateLimit;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面：拦截标有 @RateLimit 的方法，基于客户端 IP + 方法名计数。
 * 超出限制时直接向响应写入 429 JSON，不执行原方法。
 */
@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Around("@annotation(top.dlsloveyy.backendtest.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();
        String ip = getClientIp(request);
        String redisKey = "rate_limit:" + ip + ":" + method.getName();

        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == 1) {
            // 首次写入时设置过期时间
            redisTemplate.expire(redisKey, rateLimit.window(), TimeUnit.SECONDS);
        }

        if (count > rateLimit.max()) {
            HttpServletResponse response = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getResponse();
            if (response != null) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                String body = new ObjectMapper().writeValueAsString(
                        Map.of("code", 429, "message",
                                "操作过于频繁，请 " + rateLimit.window() + " 秒后再试"));
                response.getWriter().write(body);
            }
            return null;
        }

        return point.proceed();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP（代理链），取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
