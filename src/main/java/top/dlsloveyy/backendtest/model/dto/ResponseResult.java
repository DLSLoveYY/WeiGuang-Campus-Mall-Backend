package top.dlsloveyy.backendtest.model.dto; // ✅ 注意这里补上了 .dto

import lombok.Data;

/**
 * 统一的 API 响应结果封装类
 */
@Data
public class ResponseResult<T> {

    private Integer code; // 状态码 (200:成功, 其他:失败)
    private String message; // 提示信息
    private T data; // 返回的核心数据

    // 默认构造函数
    public ResponseResult() {}

    // 全参构造函数
    public ResponseResult(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // --- 成功的便捷方法 ---
    public static <T> ResponseResult<T> success() {
        return new ResponseResult<>(200, "操作成功", null);
    }

    public static <T> ResponseResult<T> success(T data) {
        return new ResponseResult<>(200, "操作成功", data);
    }

    public static <T> ResponseResult<T> success(String message, T data) {
        return new ResponseResult<>(200, message, data);
    }

    // --- 失败的便捷方法 ---
    public static <T> ResponseResult<T> error(String message) {
        return new ResponseResult<>(500, message, null);
    }

    public static <T> ResponseResult<T> error(Integer code, String message) {
        return new ResponseResult<>(code, message, null);
    }
}