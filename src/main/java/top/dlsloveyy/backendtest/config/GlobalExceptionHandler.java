package top.dlsloveyy.backendtest.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;

/**
 * 统一异常处理：将业务异常转换为统一 JSON 响应，避免前端收到默认 500 HTML。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex) {
        // 静态资源404属于常见访问行为，不应按系统异常打印ERROR堆栈。
        log.debug("静态资源不存在: {}", ex.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseResult<?> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "操作失败，请稍后重试"
                : ex.getMessage();
        log.warn("业务异常: {}", message);
        return ResponseResult.error(message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseResult<?> handleException(Exception ex) {
        log.error("系统异常", ex);
        return ResponseResult.error("系统异常，请稍后重试");
    }
}
