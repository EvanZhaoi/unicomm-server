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
 * <p>处理桌面端 Windows 用户的身份认证请求.</p>
 *
 * @author UniComm Team
 * @version 0.1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证模块", description = "桌面端 Windows 用户认证接口")
public class AuthController {

    private final AuthService authService;

    /**
     * 桌面端验证 Windows 用户身份.
     *
     * <p>客户端携带 Windows 用户信息进行认证，服务端验证用户状态后
     * 返回用户详细信息和访问令牌.</p>
     *
     * @param request 桌面认证请求 (username, domain, computerName, deviceId, os, osVersion, appVersion)
     * @return 认证结果 (用户信息 + accessToken)
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

        DesktopVerifyResponse response = authService.desktopVerify(request);

        return Result.success(response);
    }
}
