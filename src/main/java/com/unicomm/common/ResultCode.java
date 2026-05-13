package com.unicomm.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应码枚举类.
 *
 * <p>定义系统中所有标准化的响应码，每个响应码包含状态码和默认消息。</p>
 *
 * <p><strong>响应码分类:</strong></p>
 * <ul>
 *   <li>2xx - 成功</li>
 *   <li>4xx - 客户端错误
 *     <ul>
 *       <li>4xx (通用) - BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, METHOD_NOT_ALLOWED</li>
 *       <li>4xx (业务) - USER_NOT_FOUND, USER_DISABLED, TOKEN_INVALID, PERMISSION_DENIED</li>
 *     </ul>
 *   </li>
 *   <li>5xx - 服务端错误</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see Result
 */
@Getter
@AllArgsConstructor
@Schema(description = "响应码枚举")
public enum ResultCode {

    /* ==================== 成功 ==================== */

    /**
     * 成功.
     *
     * <p>默认的成功响应码。</p>
     */
    SUCCESS(200, "success"),

    /* ==================== 通用客户端错误 ==================== */

    /**
     * 请求参数错误.
     *
     * <p>客户端提交的参数不符合要求，如缺少必填参数、格式错误等。</p>
     */
    BAD_REQUEST(400, "请求参数错误"),

    /**
     * 未授权.
     *
     * <p>请求需要认证但未提供有效凭证。</p>
     */
    UNAUTHORIZED(401, "未授权"),

    /**
     * 禁止访问.
     *
     * <p>请求的资源禁止访问，可能是权限不足。</p>
     */
    FORBIDDEN(403, "禁止访问"),

    /**
     * 资源不存在.
     *
     * <p>请求的资源在服务器上不存在。</p>
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 请求方法不允许.
     *
     * <p>不支持该 HTTP 方法，如使用了 GET 而接口只支持 POST。</p>
     */
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),

    /* ==================== 业务错误 ==================== */

    /**
     * 用户不存在.
     *
     * <p>认证时找不到对应的用户。</p>
     */
    USER_NOT_FOUND(401, "用户不存在"),

    /**
     * 用户已禁用.
     *
     * <p>用户存在但状态为禁用，无法登录。</p>
     */
    USER_DISABLED(401, "当前 Windows 用户未开通 UniComm 权限"),

    /**
     * Token 无效或已过期.
     *
     * <p>认证令牌无效、已过期或被篡改。</p>
     */
    TOKEN_INVALID(401, "Token 无效或已过期"),

    /**
     * 权限不足.
     *
     * <p>用户没有执行该操作的权限。</p>
     */
    PERMISSION_DENIED(403, "权限不足"),

    /* ==================== 服务端错误 ==================== */

    /**
     * 服务器内部错误.
     *
     * <p>服务器发生未预期的异常。</p>
     */
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),

    /**
     * 服务暂不可用.
     *
     * <p>服务临时不可用，可能由于维护或过载。</p>
     */
    SERVICE_UNAVAILABLE(503, "服务暂不可用");

    /**
     * HTTP 状态码.
     *
     * <p>符合 HTTP 标准的状态码约定。</p>
     */
    private final int code;

    /**
     * 默认错误消息.
     *
     * <p>用于错误响应的默认提示信息，可在 Result.error() 中覆盖。</p>
     */
    private final String message;
}