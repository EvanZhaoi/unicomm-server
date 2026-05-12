package com.unicomm.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应码枚举类.
 *
 * @author UniComm Team
 * @version 0.1.0
 */
@Getter
@AllArgsConstructor
@Schema(description = "响应码枚举")
public enum ResultCode {

    /* 成功 */
    SUCCESS(200, "success"),

    /* 客户端错误 - 4xx */
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),

    /* 业务错误 - 4xx */
    USER_NOT_FOUND(401, "用户不存在"),
    USER_DISABLED(401, "当前 Windows 用户未开通 UniComm 权限"),
    TOKEN_INVALID(401, "Token 无效或已过期"),
    PERMISSION_DENIED(403, "权限不足"),

    /* 服务端错误 - 5xx */
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用");

    private final int code;
    private final String message;
}
