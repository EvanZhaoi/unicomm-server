package com.unicomm.module.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.unicomm.common.BusinessException;
import com.unicomm.common.ResultCode;
import com.unicomm.module.auth.dto.DeviceVerificationRequest;
import com.unicomm.module.auth.dto.DesktopVerifyRequest;
import com.unicomm.module.auth.dto.DesktopVerifyResponse;
import com.unicomm.module.auth.dto.TokenRefreshResponse;
import com.unicomm.module.auth.integration.EmployeeInfo;
import com.unicomm.module.auth.integration.PersonnelProvider;
import com.unicomm.module.auth.service.AuthService;
import com.unicomm.module.auth.service.AuthAuditService;
import com.unicomm.module.auth.service.DeviceTrustService;
import com.unicomm.module.auth.service.UserSnapshotService;
import com.unicomm.module.member.dto.MemberSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 认证服务实现类.
 *
 * <p><strong>当前阶段:</strong> 通过 {@link PersonnelProvider} 查询人员信息，
 * 默认实现为 mock 测试人员源，不接入真实公司人员 API。</p>
 * <p><strong>后续阶段:</strong> 新增真实 HTTP/LDAP 人员适配器，并通过配置替换
 * 当前 mock 实现。</p>
 *
 * <p><strong>认证流程:</strong></p>
 * <ol>
 *   <li>桌面端读取 Windows 用户信息 (username, domain, deviceId...)</li>
 *   <li>桌面端调用 POST /api/v1/auth/desktop/verify</li>
 *   <li>后端根据 domain + username 查询人员适配器</li>
 *   <li>如果人员适配器返回有效员工并且状态正常，则创建会话 Token</li>
 *   <li>如果人员适配器返回失败/用户不存在/用户停用，则拒绝访问</li>
 *   <li>后续接入正式人员接口后，可按需保存认证审计记录</li>
 *   <li>返回 accessToken 给桌面端</li>
 * </ol>
 *
 * <p><strong>安全原则:</strong></p>
 * <ul>
 *   <li>不能因为前端传了 username 就直接信任</li>
 *   <li>必须由后端人员适配器校验身份</li>
 *   <li>后端签发 Token 后，Memo 数据通过 owner_username 进行隔离</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see AuthService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final PersonnelProvider personnelProvider;
    private final UserSnapshotService userSnapshotService;
    private final AuthAuditService authAuditService;
    private final DeviceTrustService deviceTrustService;

    /**
     * 桌面端 Windows 用户认证.
     *
     * <p><strong>详细流程:</strong></p>
     * <ol>
     *   <li>接收桌面端请求 (username, domain, computerName, deviceId...)</li>
     *   <li>通过人员适配器获取员工信息 (测试阶段使用 mock 数据)</li>
     *   <li>如果人员不存在或状态为 inactive，返回 401</li>
     *   <li>调用 Sa-Token.login() 签发会话 Token</li>
     *   <li>后续接入正式人员接口后，可按需保存认证审计记录</li>
     *   <li>返回包含 username, employeeNo, displayName, accessToken 的响应</li>
     * </ol>
     *
     * @param request 桌面认证请求
     * @return 认证响应
     * @throws BusinessException 用户不存在或已停用
     * @since 0.1.0
     */
    @Override
    public DesktopVerifyResponse desktopVerify(DesktopVerifyRequest request) {
        log.info("桌面认证请求: domain={}, username={}, computerName={}",
                request.getDomain(), request.getUsername(), request.getComputerName());

        // ---- 1. 通过人员适配器获取员工信息 ----
        EmployeeInfo emp = personnelProvider
                .findByWindowsAccount(request.getDomain(), request.getUsername())
                .orElse(null);

        // ---- 2. 检查员工是否存在 ----
        if (emp == null) {
            log.warn("人员适配器返回: 用户不存在 - domain={}, username={}",
                    request.getDomain(), request.getUsername());
            authAuditService.record(request.getUsername(), "desktop_verify", "fail", request, "用户不存在");
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // ---- 3. 检查员工状态 ----
        if (!emp.active()) {
            log.warn("人员适配器返回: 用户已停用 - username={}, status={}", emp.username(), emp.status());
            authAuditService.record(emp.username(), "desktop_verify", "fail", request, "用户已停用");
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        userSnapshotService.saveOrUpdate(emp);

        Optional<String> verificationId = deviceTrustService
                .createVerificationIfDeviceUntrusted(emp.username(), emp.email(), request);
        if (verificationId.isPresent()) {
            authAuditService.record(emp.username(), "desktop_verify", "verification_required",
                    request, "设备未信任，已生成验证码");
            return baseResponse(emp)
                    .deviceVerificationRequired(true)
                    .verificationId(verificationId.get())
                    .build();
        }

        DesktopVerifyResponse response = issueLoginResponse(emp);
        authAuditService.record(emp.username(), "desktop_verify", "success", request, "认证成功");

        log.info("用户认证成功: username={}, employeeNo={}",
                emp.username(), emp.employeeNo());

        return response;
    }

    @Override
    public DesktopVerifyResponse verifyDevice(DeviceVerificationRequest request) {
        DesktopVerifyRequest originalRequest = deviceTrustService
                .verifyCodeAndTrustDevice(request.getVerificationId(), request.getCode());
        EmployeeInfo emp = findActiveEmployee(originalRequest);
        userSnapshotService.saveOrUpdate(emp);
        DesktopVerifyResponse response = issueLoginResponse(emp);
        authAuditService.record(emp.username(), "device_verify", "success", originalRequest, "设备验证码通过");
        return response;
    }

    @Override
    public TokenRefreshResponse refreshToken() {
        StpUtil.checkLogin();
        StpUtil.updateLastActiveToNow();
        String username = StpUtil.getLoginIdAsString();
        String token = StpUtil.getTokenValue();
        authAuditService.record(username, "token_refresh", "success", null, "Token 活跃时间刷新成功");
        return TokenRefreshResponse.builder()
                .accessToken(token)
                .expiresAt(System.currentTimeMillis() + StpUtil.getTokenTimeout(token) * 1000)
                .build();
    }

    @Override
    public List<MemberSearchResponse> searchMembers(String keyword, Integer limit) {
        int safeLimit = limit == null || limit < 1 ? 10 : Math.min(limit, 20);

        return personnelProvider.searchMembers(keyword, safeLimit).stream()
                .map(employee -> MemberSearchResponse.builder()
                        .username(employee.username())
                        .employeeNo(employee.employeeNo())
                        .displayName(employee.displayName())
                        .departmentName(employee.departmentName())
                        .email(employee.email())
                        .build())
                .toList();
    }

    private EmployeeInfo findActiveEmployee(DesktopVerifyRequest request) {
        EmployeeInfo emp = personnelProvider
                .findByWindowsAccount(request.getDomain(), request.getUsername())
                .orElse(null);
        if (emp == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!emp.active()) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        return emp;
    }

    private DesktopVerifyResponse issueLoginResponse(EmployeeInfo emp) {
        StpUtil.login(emp.username());
        return baseResponse(emp)
                .accessToken(StpUtil.getTokenValue())
                .deviceVerificationRequired(false)
                .build();
    }

    private DesktopVerifyResponse.DesktopVerifyResponseBuilder baseResponse(EmployeeInfo emp) {
        return DesktopVerifyResponse.builder()
                .username(emp.username())
                .employeeNo(emp.employeeNo())
                .displayName(emp.displayName())
                .departmentName(emp.departmentName())
                .email(emp.email())
                .permissions(List.of("memo:read", "memo:write"));
    }
}
