package com.unicomm.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器.
 *
 * <p>统一处理应用中所有未被捕获的异常，返回标准化的 {@link Result} 响应结构。</p>
 *
 * <p><strong>处理范围:</strong></p>
 * <ul>
 *   <li>业务异常 (BusinessException) - 业务逻辑错误</li>
 *   <li>认证异常 (Sa-Token) - 登录、权限、角色验证</li>
 *   <li>参数校验异常 - 请求参数验证失败</li>
 *   <li>HTTP 异常 - 请求方法不支持、资源不存在等</li>
 *   <li>兜底异常 - 所有其他未预期的异常</li>
 * </ul>
 *
 * <p><strong>设计原则:</strong></p>
 * <ul>
 *   <li>业务异常返回 HTTP 200 + 非 200 的 code (前端统一通过 code 判断)</li>
 *   <li>其他异常返回对应的 HTTP 状态码</li>
 *   <li>所有异常都记录日志，便于排查问题</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see BusinessException
 * @see Result
 * @see ResultCode
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常 ====================

    /**
     * 处理业务异常 (BusinessException).
     *
     * <p>业务异常是预期的错误，用于处理业务逻辑中的不合规情况。
     * 返回 HTTP 200 但响应 code 不为 200，前端通过 code 统一判断。</p>
     *
     * @param e 业务异常
     * @return 错误响应 Result
     * @since 0.1.0
     * @see BusinessException
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    // ==================== Sa-Token 认证异常 ====================

    /**
     * 处理未登录异常 (NotLoginException).
     *
     * <p>当用户未登录或 Token 无效时抛出。</p>
     *
     * @param e Sa-Token 未登录异常
     * @return HTTP 401 + 错误响应 Result
     * @since 0.1.0
     * @see NotLoginException
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        log.warn("未登录或 Token 失效: {}", e.getMessage());
        return Result.error(ResultCode.UNAUTHORIZED.getCode(), "请先登录");
    }

    /**
     * 处理权限不足异常 (NotPermissionException).
     *
     * <p>当用户缺少所需权限时抛出。</p>
     *
     * @param e Sa-Token 权限不足异常
     * @return HTTP 403 + 错误响应 Result
     * @since 0.1.0
     * @see NotPermissionException
     */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotPermissionException(NotPermissionException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.error(ResultCode.PERMISSION_DENIED);
    }

    /**
     * 处理角色不足异常 (NotRoleException).
     *
     * <p>当用户缺少所需角色时抛出。</p>
     *
     * @param e Sa-Token 角色不足异常
     * @return HTTP 403 + 错误响应 Result
     * @since 0.1.0
     * @see NotRoleException
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotRoleException(NotRoleException e) {
        log.warn("角色不足: {}", e.getMessage());
        return Result.error(ResultCode.PERMISSION_DENIED);
    }

    // ==================== 参数校验异常 ====================

    /**
     * 处理参数校验异常 (JSON 请求体).
     *
     * <p>当使用 @Valid 注解进行参数校验失败时抛出。</p>
     * <p>将所有字段错误消息用分号连接后返回。</p>
     *
     * @param e 参数校验异常
     * @return HTTP 400 + 错误响应 Result
     * @since 0.1.0
     * @see MethodArgumentNotValidException
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 处理参数绑定异常 (表单提交).
     *
     * <p>当表单参数绑定失败时抛出，如类型转换错误等。</p>
     *
     * @param e 参数绑定异常
     * @return HTTP 400 + 错误响应 Result
     * @since 0.1.0
     * @see BindException
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    // ==================== HTTP 异常 ====================

    /**
     * 处理请求方法不支持异常.
     *
     * <p>当使用了 HTTP 方法但接口不支持时抛出，如接口只支持 POST 但收到 GET。</p>
     *
     * @param e 请求方法不支持异常
     * @return HTTP 405 + 错误响应 Result
     * @since 0.1.0
     * @see HttpRequestMethodNotSupportedException
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.error(ResultCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 处理 404 异常.
     *
     * <p>当请求的资源不存在时抛出。</p>
     *
     * @param e 404 异常
     * @return HTTP 404 + 错误响应 Result
     * @since 0.1.0
     * @see NoHandlerFoundException
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoHandlerFoundException e) {
        log.warn("资源不存在: {}", e.getRequestURL());
        return Result.error(ResultCode.NOT_FOUND);
    }

    // ==================== 兜底异常 ====================

    /**
     * 处理所有未捕获异常 (兜底).
     *
     * <p>作为最后一道防线，处理所有上述处理器未覆盖的异常。
     * 通常是代码或基础设施的未预期错误。</p>
     *
     * <p><strong>注意:</strong> 此异常会被记录为 ERROR 级别，表明是服务器内部问题。</p>
     *
     * @param e 未捕获的异常
     * @return HTTP 500 + 错误响应 Result
     * @since 0.1.0
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("服务器内部错误", e);
        return Result.error(ResultCode.INTERNAL_SERVER_ERROR);
    }
}