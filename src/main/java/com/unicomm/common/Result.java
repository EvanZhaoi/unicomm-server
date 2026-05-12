package com.unicomm.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应结果封装类.
 *
 * <p>所有 API 响应均使用此结构:</p>
 * <pre>
 * {
 *   "code": 200,
 *   "message": "success",
 *   "data": {...}
 * }
 * </pre>
 *
 * @author UniComm Team
 * @version 0.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一响应结果")
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "状态码: 200=成功, 4xx=客户端错误, 5xx=服务端错误")
    private int code;

    @Schema(description = "提示信息")
    private String message;

    @Schema(description = "响应数据")
    private T data;

    /**
     * 构建成功响应 (无数据).
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 构建成功响应 (带数据).
     *
     * @param data 响应数据
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构建成功响应 (带自定义消息和数据).
     *
     * @param message 消息
     * @param data    数据
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 构建失败响应.
     *
     * @param resultCode 响应码
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 构建失败响应 (带自定义消息).
     *
     * @param resultCode 响应码
     * @param message    自定义消息
     */
    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /**
     * 构建失败响应 (带状态码和消息).
     *
     * @param code    状态码
     * @param message 消息
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
