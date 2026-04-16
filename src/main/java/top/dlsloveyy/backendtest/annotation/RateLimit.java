package top.dlsloveyy.backendtest.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解：在方法上标注，启用基于 IP 的滑动窗口限流。
 * max: 窗口内最大请求次数（默认5次）
 * window: 窗口时长（秒，默认60秒）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    int max() default 5;
    int window() default 60;
}
