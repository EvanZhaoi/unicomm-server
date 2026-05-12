package com.unicomm.module.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.unicomm.common.BusinessException;
import com.unicomm.common.ResultCode;
import com.unicomm.module.auth.dto.DesktopVerifyRequest;
import com.unicomm.module.auth.dto.DesktopVerifyResponse;
import com.unicomm.module.auth.entity.UniUser;
import com.unicomm.module.auth.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务实现类 (Phase 1 - 内存数据).
 *
 * <p>Phase 1 使用内存数据模拟用户数据，无真实数据库依赖.
     * Phase 2 将切换为 MyBatis Plus + MySQL 真实数据.</p>
 *
 * @author UniComm Team
 * @version 0.1.0
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /**
     * 内存用户存储 (Phase 1 替代数据库).
     * Key: windowsDomain#windowsUsername
     */
    private final Map<String, UniUser> userCache = new ConcurrentHashMap<>();

    @Value("${unicomm.data-mode:dev}")
    private String dataMode;

    /**
     * 初始化种子数据 (Phase 1).
     */
    @PostConstruct
    public void initSeedData() {
        if (!"dev".equals(dataMode)) {
            log.info("data-mode={}, 跳过内存数据初始化", dataMode);
            return;
        }

        log.info("Phase 1: 初始化内存用户数据...");

        // 种子用户 - Evan Zhao
        UniUser user1 = new UniUser();
        user1.setId(10001L);
        user1.setEmployeeNo("E10001");
        user1.setDisplayName("Evan Zhao");
        user1.setDepartmentId(1L);
        user1.setDepartmentName("IT Department");
        user1.setStatus(1);
        user1.setWindowsUsername("evan.zhao");
        user1.setWindowsDomain("COMPANY");
        userCache.put("COMPANY#evan.zhao", user1);

        // 种子用户 - Alice Wang
        UniUser user2 = new UniUser();
        user2.setId(10002L);
        user2.setEmployeeNo("E10002");
        user2.setDisplayName("Alice Wang");
        user2.setDepartmentId(2L);
        user2.setDepartmentName("HR Department");
        user2.setStatus(1);
        user2.setWindowsUsername("alice.wang");
        user2.setWindowsDomain("COMPANY");
        userCache.put("COMPANY#alice.wang", user2);

        // 禁用用户 - Bob Li (status=0)
        UniUser user3 = new UniUser();
        user3.setId(10003L);
        user3.setEmployeeNo("E10003");
        user3.setDisplayName("Bob Li");
        user3.setDepartmentId(1L);
        user3.setDepartmentName("IT Department");
        user3.setStatus(0); // 禁用
        user3.setWindowsUsername("bob.li");
        user3.setWindowsDomain("COMPANY");
        userCache.put("COMPANY#bob.li", user3);

        log.info("内存用户数据初始化完成, 共 {} 条记录", userCache.size());
    }

    /**
     * 桌面端 Windows 用户认证.
     *
     * @param request 桌面认证请求
     * @return 认证响应
     * @throws BusinessException 用户不存在或已被禁用
     */
    @Override
    public DesktopVerifyResponse desktopVerify(DesktopVerifyRequest request) {
        String key = buildUserKey(request.getDomain(), request.getUsername());

        log.info("桌面认证请求: domain={}, username={}, computerName={}",
                request.getDomain(), request.getUsername(), request.getComputerName());

        // 1. 查找用户
        UniUser user = userCache.get(key);
        if (user == null) {
            log.warn("用户不存在: {}", key);
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 2. 检查用户状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("用户已禁用: {}", key);
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 3. 签发 Sa-Token Token
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        // 4. 构建权限列表 (Phase 1 简化: 所有启用用户都有 memo 权限)
        List<String> permissions = List.of("memo:read", "memo:write");

        // 5. 构建响应
        DesktopVerifyResponse response = DesktopVerifyResponse.builder()
                .userId(user.getId())
                .employeeNo(user.getEmployeeNo())
                .displayName(user.getDisplayName())
                .departmentName(user.getDepartmentName())
                .permissions(permissions)
                .accessToken(token)
                .build();

        log.info("用户认证成功: employeeNo={}, token={}", user.getEmployeeNo(), token);

        return response;
    }

    /**
     * 构建用户缓存 Key.
     */
    private String buildUserKey(String domain, String username) {
        return (domain == null ? "" : domain) + "#" + (username == null ? "" : username);
    }
}
