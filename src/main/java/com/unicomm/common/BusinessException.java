package com.unicomm.common;

import lombok.Getter;

/**
 * 业务异常类.
 *
 * <p>用于封装业务逻辑错误，区别于系统异常 (如空指针、数据库异常等)。</p>
 *
 * <p><strong>与系统异常的区别:</strong></p>
 * <ul>
 *   <li>业务异常是预期的错误，用于处理业务逻辑中的不合规情况</li>
 *   <li>系统异常是未预期的错误，通常表示代码或基础设施问题</li>
 * </ul>
 *
 * <p><strong>使用示例:</strong></p>
 * <pre>
 * // 使用预定义 ResultCode
 * throw new BusinessException(ResultCode.USER_NOT_FOUND);
 *
 * // 使用预定义 ResultCode + 自定义消息
 * throw new BusinessException(ResultCode.BAD_REQUEST, "用户名不能为空");
 *
 * // 使用自定义状态码和消息
 * throw new BusinessException(400, "余额不足");
 * </pre>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see ResultCode
 * @see GlobalExceptionHandler
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误状态码.
     *
     * <p>与 HTTP 状态码概念相似但不完全对应，用于 Result 响应结构。</p>
     */
    private final int code;

    /**
     * 使用 ResultCode 构造业务异常.
     *
     * <p>适用于使用预定义错误码的场景。</p>
     *
     * @param resultCode 响应码枚举，不能为 null
     * @since 0.1.0
     * @example
     * throw new BusinessException(ResultCode.USER_NOT_FOUND);
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 使用 ResultCode 和自定义消息构造业务异常.
     *
     * <p>适用于需要更详细错误信息的场景，将覆盖 ResultCode 中的默认消息。</p>
     *
     * @param resultCode 响应码枚举，不能为 null
     * @param message    自定义错误消息，将覆盖 resultCode.getMessage()
     * @since 0.1.0
     * @example
     * throw new BusinessException(ResultCode.BAD_REQUEST, "用户名不能为空且不能超过50字符");
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /**
     * 使用自定义状态码和消息构造业务异常.
     *
     * <p>适用于没有对应 ResultCode 枚举的场景。</p>
     *
     * @param code    HTTP 状态码 (建议使用 4xx 或 5xx)
     * @param message 错误消息
     * @since 0.1.0
     * @example
     * throw new BusinessException(400, "自定义业务错误");
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}