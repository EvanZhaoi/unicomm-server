package com.unicomm.module.auth.controller;

import com.unicomm.common.Result;
import com.unicomm.module.auth.dto.DesktopVerifyRequest;
import com.unicomm.module.auth.dto.DesktopVerifyResponse;
import com.unicomm.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器.
 *
 * <p>处理桌面端 Windows 用户的身份认证请求。</p>
 *
 * <p><strong>主要功能:</strong></p>
 * <ul>
 *   <li>桌面端身份验证 - 验证 Windows 用户身份并获取访问令牌</li>
 * </ul>
 *
 * <p><strong>认证流程:</strong></p>
 * <ol>
 *   <li>桌面客户端启动时，携带 Windows 用户信息调用此接口</li>
 *   <li>服务端验证用户状态 (存在、启用)</li>
 *   <li>验证通过后签发 Sa-Token Token</li>
 *   <li>客户端存储 Token，后续请求携带 Token 访问受保护资源</li>
 * </ol>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see AuthService
 * @see DesktopVerifyRequest
 * @see DesktopVerifyResponse
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证模块", description = "桌面端 Windows 用户认证接口")
public class AuthController {

    /**
     * 认证服务.
     *
     * <p>处理桌面端认证的业务逻辑。</p>
     */
    private final AuthService authService;

    /**
     * 桌面端验证 Windows 用户身份.
     *
     * <p><strong>接口信息:</strong></p>
     * <ul>
     *   <li>URL: POST /api/v1/auth/desktop/verify</li>
     *   <li>Content-Type: application/json</li>
     * </ul>
     *
     * <p><strong>请求体 (DesktopVerifyRequest):</strong></p>
     * <pre>
     * {
     *   "username": "evan.zhao",      // Windows 用户名 (必填)
     *   "domain": "COMPANY",          // Windows 域 (必填)
     *   "computerName": "CN-SH-001",  // 计算机名称 (可选)
     *   "deviceId": "xxx",            // 客户端设备 ID (可选)
     *   "os": "Windows",              // 操作系统类型 (可选)
     *   "osVersion": "Windows 11",    // 操作系统版本 (可选)
     *   "appVersion": "0.1.0"         // 客户端应用版本 (可选)
     * }
     * </pre>
     *
     * <p><strong>成功响应 (200):</strong></p>
     * <pre>
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "username": "evan.zhao",
     *     "employeeNo": "E10001",
     *     "displayName": "Evan Zhao",
     *     "departmentName": "IT Department",
     *     "permissions": ["memo:read", "memo:write"],
     *     "accessToken": "xxxxx"
     *   }
     * }
     * </pre>
     *
     * <p><strong>错误响应:</strong></p>
     * <ul>
     *   <li>400 - 请求参数错误 (缺少必填字段)</li>
     *   <li>401 - 用户不存在或已禁用 (响应 code 为 401)</li>
     * </ul>
     *
     * @param request 桌面认证请求，包含 Windows 用户信息
     * @return 认证结果 (用户信息 + accessToken)
     * @since 0.1.0
     * @see DesktopVerifyRequest
     * @see DesktopVerifyResponse
     * @see Result
     */
    @PostMapping("/desktop/verify")
    @Operation(
            summary = "桌面端认证",
            description = "验证 Windows 用户身份并获取访问令牌"
    )
    public Result<DesktopVerifyResponse> desktopVerify(
            @Valid @RequestBody DesktopVerifyRequest request) {

        log.info("收到桌面认证请求: username={}, domain={}, computerName={}",
                request.getUsername(), request.getDomain(), request.getComputerName());

        // 调用认证服务进行身份验证
        DesktopVerifyResponse response = authService.desktopVerify(request);

        return Result.success(response);
    }
}