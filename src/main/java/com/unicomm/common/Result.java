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
 * <p>所有 API 响应均使用此结构确保一致性:</p>
 * <pre>
 * {
 *   "code": 200,           // 响应码 (200=成功, 4xx=客户端错误, 5xx=服务端错误)
 *   "message": "success",   // 人类可读的消息
 *   "data": {...}           // 实际响应数据 (可以为 null)
 * }
 * </pre>
 *
 * <p><strong>使用示例:</strong></p>
 * <pre>
 * // 返回成功响应 (带数据)
 * return Result.success(userList);
 *
 * // 返回成功响应 (无数据)
 * return Result.success();
 *
 * // 返回带自定义消息的成功响应
 * return Result.success("操作完成", data);
 *
 * // 返回错误响应 (使用预定义错误码)
 * return Result.error(ResultCode.BAD_REQUEST);
 *
 * // 返回错误响应 (使用预定义错误码 + 自定义消息)
 * return Result.error(ResultCode.BAD_REQUEST, "用户名不能为空");
 *
 * // 返回错误响应 (使用自定义状态码和消息)
 * return Result.error(500, "数据库连接失败");
 * </pre>
 *
 * <p><strong>响应码规范:</strong></p>
 * <ul>
 *   <li>2xx - 成功</li>
 *   <li>4xx - 客户端错误 (参数错误、未授权、权限不足、资源不存在等)</li>
 *   <li>5xx - 服务端错误 (内部错误、服务暂不可用等)</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see ResultCode
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一响应结果")
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 状态码.
     *
     * <p>约定:</p>
     * <ul>
     *   <li>200 = 成功</li>
     *   <li>4xx = 客户端错误</li>
     *   <li>5xx = 服务端错误</li>
     * </ul>
     */
    @Schema(description = "状态码: 200=成功, 4xx=客户端错误, 5xx=服务端错误")
    private int code;

    /**
     * 提示信息.
     *
     * <p>用于向用户展示操作结果或错误原因的简短描述。</p>
     */
    @Schema(description = "提示信息")
    private String message;

    /**
     * 响应数据.
     *
     * <p>可以是任何类型: 对象、列表、字符串、数字等，也可以为 null。</p>
     */
    @Schema(description = "响应数据")
    private T data;

    /**
     * 构建成功响应 (无数据).
     *
     * <p>适用于不需要返回数据的操作，如删除、修改等。</p>
     *
     * @param <T> 响应数据类型
     * @return code=200, message="success", data=null 的响应
     * @since 0.1.0
     * @example
     * // 删除操作成功
     * return Result.success();
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 构建成功响应 (带数据).
     *
     * <p>最常用的成功响应构建方式。</p>
     *
     * @param data 响应数据 (可以是任何类型，也可以为 null)
     * @param <T>  响应数据类型
     * @return code=200, message="success", data=入参数据的响应
     * @since 0.1.0
     * @example
     * // 返回用户列表
     * return Result.success(userList);
     *
     * // 返回单个用户
     * return Result.success(user);
     *
     * // 返回 null (明确表示成功但无数据)
     * return Result.success(null);
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构建成功响应 (带自定义消息和数据).
     *
     * <p>适用于需要返回明确提示信息的成功响应场景。</p>
     *
     * @param message 自定义消息，将覆盖默认的 "success"
     * @param data    响应数据
     * @param <T>     响应数据类型
     * @return code=200, message=入参message, data=入参data 的响应
     * @since 0.1.0
     * @example
     * return Result.success("用户创建成功", newUser);
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 构建失败响应 (使用预定义响应码).
     *
     * <p>自动使用 ResultCode 中定义的状态码和默认消息。</p>
     *
     * @param resultCode 响应码枚举，不能为 null
     * @param <T>        响应数据类型
     * @return 使用 resultCode.code 和 resultCode.message 的错误响应
     * @since 0.1.0
     * @see ResultCode
     * @example
     * return Result.error(ResultCode.BAD_REQUEST);
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 构建失败响应 (使用预定义响应码 + 自定义消息).
     *
     * <p>使用 ResultCode 中的状态码，但用自定义消息替代默认消息。</p>
     * <p>适用于需要在错误响应中提供更详细错误信息的场景。</p>
     *
     * @param resultCode 响应码枚举，不能为 null
     * @param message    自定义错误消息，将覆盖 ResultCode 中的默认消息
     * @param <T>        响应数据类型
     * @return 使用 resultCode.code 和入参 message 的错误响应
     * @since 0.1.0
     * @see ResultCode
     * @example
     * return Result.error(ResultCode.BAD_REQUEST, "用户名不能为空且不能超过50字符");
     */
    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /**
     * 构建失败响应 (使用自定义状态码和消息).
     *
     * <p>适用于没有对应 ResultCode 枚举的自定义错误场景。</p>
     *
     * @param code    HTTP 状态码 (建议使用 4xx 或 5xx)
     * @param message 错误消息
     * @param <T>     响应数据类型
     * @return 使用入参 code 和 message 的错误响应
     * @since 0.1.0
     * @example
     * return Result.error(503, "服务暂不可用，请稍后重试");
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}