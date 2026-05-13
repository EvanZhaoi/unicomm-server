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
 * 认证服务实现类.
 *
 * <p><strong>Phase 1:</strong> 使用内存数据模拟用户数据，无真实数据库依赖。
 * 此模式适用于开发和测试阶段。</p>
 * <p><strong>Phase 2:</strong> 将切换为 MyBatis Plus + MySQL 真实数据存储。</p>
 *
 * <p><strong>内存数据结构:</strong></p>
 * <ul>
 *   <li>Key: windowsDomain#windowsUsername (如 "COMPANY#evan.zhao")</li>
 *   <li>Value: UniUser 实体对象</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see AuthService
 * @see UniUser
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /**
     * 内存用户存储 (Phase 1 替代数据库).
     *
     * <p>使用 ConcurrentHashMap 保证线程安全。</p>
     * <p>Key 格式: domain#username</p>
     */
    private final Map<String, UniUser> userCache = new ConcurrentHashMap<>();

    /**
     * 数据模式配置.
     *
     * <p>通过 unicomm.data-mode 配置项控制:</p>
     * <ul>
     *   <li>dev: 启用内存种子数据</li>
     *   <li>其他: 跳过内存数据初始化 (使用真实数据库)</li>
     * </ul>
     */
    @Value("${unicomm.data-mode:dev}")
    private String dataMode;

    /**
     * 初始化种子数据 (Phase 1).
     *
     * <p>在 Spring 容器初始化完成后执行，用于预加载测试用户数据。</p>
     *
     * <p><strong>种子用户:</strong></p>
     * <ul>
     *   <li>Evan Zhao (E10001) - IT Department, 启用</li>
     *   <li>Alice Wang (E10002) - HR Department, 启用</li>
     *   <li>Bob Li (E10003) - IT Department, 禁用</li>
     * </ul>
     *
     * @since 0.1.0
     * @see UniUser
     */
    @PostConstruct
    public void initSeedData() {
        // 非 dev 模式跳过内存数据初始化
        if (!"dev".equals(dataMode)) {
            log.info("data-mode={}, 跳过内存数据初始化", dataMode);
            return;
        }

        log.info("Phase 1: 初始化内存用户数据...");

        // ===== 种子用户 1: Evan Zhao =====
        UniUser user1 = new UniUser();
        user1.setId(10001L);
        user1.setEmployeeNo("E10001");
        user1.setDisplayName("Evan Zhao");
        user1.setDepartmentId(1L);
        user1.setDepartmentName("IT Department");
        user1.setStatus(1);  // 启用
        user1.setWindowsUsername("evan.zhao");
        user1.setWindowsDomain("COMPANY");
        userCache.put("COMPANY#evan.zhao", user1);

        // ===== 种子用户 2: Alice Wang =====
        UniUser user2 = new UniUser();
        user2.setId(10002L);
        user2.setEmployeeNo("E10002");
        user2.setDisplayName("Alice Wang");
        user2.setDepartmentId(2L);
        user2.setDepartmentName("HR Department");
        user2.setStatus(1);  // 启用
        user2.setWindowsUsername("alice.wang");
        user2.setWindowsDomain("COMPANY");
        userCache.put("COMPANY#alice.wang", user2);

        // ===== 种子用户 3: Bob Li (禁用用户) =====
        UniUser user3 = new UniUser();
        user3.setId(10003L);
        user3.setEmployeeNo("E10003");
        user3.setDisplayName("Bob Li");
        user3.setDepartmentId(1L);
        user3.setDepartmentName("IT Department");
        user3.setStatus(0);  // 禁用
        user3.setWindowsUsername("bob.li");
        user3.setWindowsDomain("COMPANY");
        userCache.put("COMPANY#bob.li", user3);

        log.info("内存用户数据初始化完成, 共 {} 条记录", userCache.size());
    }

    /**
     * 桌面端 Windows 用户认证.
     *
     * <p><strong>处理流程:</strong></p>
     * <ol>
     *   <li>根据 domain#username 构建用户 key 并查找用户</li>
     *   <li>若用户不存在，抛出业务异常</li>
     *   <li>检查用户状态，若已禁用，抛出业务异常</li>
     *   <li>调用 Sa-Token.login() 签发 Token</li>
     *   <li>构建并返回包含用户信息和 Token 的响应</li>
     * </ol>
     *
     * @param request 桌面认证请求
     * @return 认证响应 (DesktopVerifyResponse)
     * @throws BusinessException 用户不存在或已被禁用
     * @since 0.1.0
     * @see DesktopVerifyRequest
     * @see DesktopVerifyResponse
     */
    @Override
    public DesktopVerifyResponse desktopVerify(DesktopVerifyRequest request) {
        // 构建用户缓存 Key: domain#username
        String key = buildUserKey(request.getDomain(), request.getUsername());

        log.info("桌面认证请求: domain={}, username={}, computerName={}",
                request.getDomain(), request.getUsername(), request.getComputerName());

        // ---- 1. 查找用户 ----
        UniUser user = userCache.get(key);
        if (user == null) {
            log.warn("用户不存在: {}", key);
            // 用户不存在时返回与禁用相同的错误码，避免泄露用户是否存在
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // ---- 2. 检查用户状态 ----
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("用户已禁用: {}", key);
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // ---- 3. 签发 Sa-Token Token ----
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        // ---- 4. 构建权限列表 (Phase 1 简化实现) ----
        // Phase 1: 所有启用用户都拥有 memo 读写权限
        // Phase 2: 从数据库或配置中加载真实权限
        List<String> permissions = List.of("memo:read", "memo:write");

        // ---- 5. 构建并返回响应 ----
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
     *
     * <p>将 domain 和 username 组装为统一的 Key 格式。</p>
     * <p>格式: domain#username</p>
     * <p>示例: "COMPANY#evan.zhao"</p>
     *
     * @param domain   Windows 域 (可为 null)
     * @param username Windows 用户名 (可为 null)
     * @return 格式化的用户缓存 Key
     * @since 0.1.0
     */
    private String buildUserKey(String domain, String username) {
        return (domain == null ? "" : domain) + "#" + (username == null ? "" : username);
    }
}