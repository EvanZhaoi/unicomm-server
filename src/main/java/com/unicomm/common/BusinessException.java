package com.unicomm.common;

import lombok.Getter;

/**
 * 业务异常类.
 *
 * <p>用于封装业务逻辑错误，支持自定义错误码和错误信息.</p>
 *
 * @author UniComm Team
 * @version 0.1.0
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    /**
     * 使用 ResultCode 构造业务异常.
     *
     * @param resultCode 响应码枚举
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 使用 ResultCode 和自定义消息构造业务异常.
     *
     * @param resultCode 响应码枚举
     * @param message     自定义消息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /**
     * 使用自定义状态码和消息构造业务异常.
     *
     * @param code    状态码
     * @param message 错误消息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
