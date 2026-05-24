package com.unicomm.module.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.unicomm.common.BusinessException;
import com.unicomm.common.ResultCode;
import com.unicomm.module.auth.dto.DesktopVerifyRequest;
import com.unicomm.module.auth.dto.DesktopVerifyResponse;
import com.unicomm.module.auth.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务实现类.
 *
 * <p><strong>Phase 1:</strong> 使用内存数据模拟公司人员接口返回，
 * 此模式适用于开发和测试阶段。</p>
 * <p><strong>Phase 2:</strong> 将接入真实公司人员接口，替换内存数据。</p>
 *
 * <p><strong>认证流程:</strong></p>
 * <ol>
 *   <li>桌面端读取 Windows 用户信息 (username, domain, deviceId...)</li>
 *   <li>桌面端调用 POST /api/v1/auth/desktop/verify</li>
 *   <li>后端根据 username 调用公司人员接口获取员工信息</li>
 *   <li>如果人员接口返回有效员工并且状态正常，则创建会话 Token</li>
 *   <li>如果人员接口返回失败/用户不存在/用户停用，则拒绝访问</li>
 *   <li>后端把人员信息写入 uni_user_snapshot (缓存和审计)</li>
 *   <li>返回 accessToken 给桌面端</li>
 * </ol>
 *
 * <p><strong>安全原则:</strong></p>
 * <ul>
 *   <li>不能因为前端传了 username 就直接信任</li>
 *   <li>必须由后端调用公司人员接口校验</li>
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
public class AuthServiceImpl implements AuthService {

    /**
     * 内存用户存储 (Phase 1 替代公司人员接口).
     *
     * <p>使用 ConcurrentHashMap 保证线程安全。</p>
     * <p>Key 格式: domain#username</p>
     *
     * <p>Phase 2 将替换为调用真实公司人员接口：</p>
     * <pre>
     * // 伪代码示例
     * EmployeeInfo emp = companyHrApi.getEmployeeByUsername(username);
     * if (emp == null || !emp.isActive()) {
     *     throw new BusinessException(ResultCode.USER_NOT_FOUND);
     * }
     * </pre>
     */
    private final Map<String, EmployeeInfo> employeeCache = new ConcurrentHashMap<>();

    @Value("${unicomm.data-mode:dev}")
    private String dataMode;

    /**
     * 员工信息内部类 (Phase 1 模拟公司人员接口返回).
     *
     * <p>Phase 2 中将从公司人员接口获取真实的 EmployeeInfo。</p>
     */
    static class EmployeeInfo {
        String username;
        String employeeNo;
        String displayName;
        String departmentName;
        String email;
        String status;  // active / inactive

        EmployeeInfo(String username, String employeeNo, String displayName,
                     String departmentName, String email, String status) {
            this.username = username;
            this.employeeNo = employeeNo;
            this.displayName = displayName;
            this.departmentName = departmentName;
            this.email = email;
            this.status = status;
        }
    }

    /**
     * 初始化种子数据 (Phase 1).
     *
     * @since 0.1.0
     */
    @PostConstruct
    public void initSeedData() {
        if (!"dev".equals(dataMode)) {
            log.info("data-mode={}, 跳过内存数据初始化", dataMode);
            return;
        }

        log.info("Phase 1: 初始化内存员工数据 (模拟公司人员接口)...");

        // 种子用户 1: Evan Zhao - 正常员工
        employeeCache.put("COMPANY#evan.zhao",
            new EmployeeInfo("evan.zhao", "E10001", "Evan Zhao",
                "IT Department", "evan.zhao@company.com", "active"));

        // macOS 本机开发用户
        employeeCache.put("#evanzhao",
            new EmployeeInfo("evanzhao", "DEV001", "Evan Zhao",
                "Development", "evanzhao@local.dev", "active"));

        // 种子用户 2: Alice Wang - 正常员工
        employeeCache.put("COMPANY#alice.wang",
            new EmployeeInfo("alice.wang", "E10002", "Alice Wang",
                "HR Department", "alice.wang@company.com", "active"));

        // 种子用户 3: Bob Li - 已停用员工
        employeeCache.put("COMPANY#bob.li",
            new EmployeeInfo("bob.li", "E10003", "Bob Li",
                "IT Department", "bob.li@company.com", "inactive"));

        log.info("内存员工数据初始化完成, 共 {} 条记录", employeeCache.size());
    }

    /**
     * 桌面端 Windows 用户认证.
     *
     * <p><strong>详细流程:</strong></p>
     * <ol>
     *   <li>接收桌面端请求 (username, domain, computerName, deviceId...)</li>
     *   <li>调用公司人员接口获取员工信息 (Phase 1 使用内存模拟)</li>
     *   <li>如果人员不存在或状态为 inactive，返回 401</li>
     *   <li>调用 Sa-Token.login() 签发会话 Token</li>
     *   <li>将员工信息写入 uni_user_snapshot (缓存和审计)</li>
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
        String key = buildUserKey(request.getDomain(), request.getUsername());

        log.info("桌面认证请求: domain={}, username={}, computerName={}",
                request.getDomain(), request.getUsername(), request.getComputerName());

        // ---- 1. 调用公司人员接口获取员工信息 (Phase 1 使用内存模拟) ----
        // TODO Phase 2: 替换为真实公司人员接口调用
        // EmployeeInfo emp = companyHrApi.getEmployeeByUsername(request.getUsername());
        EmployeeInfo emp = employeeCache.get(key);

        // ---- 2. 检查员工是否存在 ----
        if (emp == null) {
            log.warn("公司人员接口返回: 用户不存在 - {}", key);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // ---- 3. 检查员工状态 ----
        if (!"active".equals(emp.status)) {
            log.warn("公司人员接口返回: 用户已停用 - {}, status={}", key, emp.status);
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // ---- 4. 签发 Sa-Token Token ----
        // 使用 username 作为会话标识，而非 userId
        StpUtil.login(emp.username);
        String token = StpUtil.getTokenValue();

        // ---- 5. TODO: 将员工信息写入 uni_user_snapshot (缓存和审计) ----
        // userSnapshotService.saveOrUpdate(emp);

        // ---- 6. 构建并返回响应 ----
        DesktopVerifyResponse response = DesktopVerifyResponse.builder()
                .username(emp.username)
                .employeeNo(emp.employeeNo)
                .displayName(emp.displayName)
                .departmentName(emp.departmentName)
                .email(emp.email)
                .permissions(List.of("memo:read", "memo:write"))
                .accessToken(token)
                .build();

        log.info("用户认证成功: username={}, employeeNo={}",
                emp.username, emp.employeeNo);

        return response;
    }

    /**
     * 构建用户缓存 Key.
     */
    private String buildUserKey(String domain, String username) {
        return (domain == null ? "" : domain) + "#" + (username == null ? "" : username);
    }
}
