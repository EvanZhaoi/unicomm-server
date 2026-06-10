package com.unicomm.module.auth.integration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试阶段人员信息适配器.
 *
 * <p>该实现只用于本地开发、测试和演示，不接入真实公司人员 API。生产环境接入真实
 * 人员系统时，应新增 HTTP/LDAP 等实现，并通过配置切换 provider。</p>
 */
@Slf4j
@Component
public class MockPersonnelProvider implements PersonnelProvider {

    private final Map<String, EmployeeInfo> employeeCache = new ConcurrentHashMap<>();

    /**
     * 初始化本地测试人员数据.
     *
     * <p>Key 格式为 {@code DOMAIN#username}。domain 为空时使用 {@code #username}，
     * 用于 macOS 或非域环境开发。</p>
     */
    @PostConstruct
    public void initSeedData() {
        log.info("初始化 mock 人员数据，当前测试阶段不接入真实人员 API");

        employeeCache.put("COMPANY#evan.zhao",
                new EmployeeInfo("evan.zhao", "E10001", "Evan Zhao",
                        "IT Department", "evan.zhao@company.com", "active"));

        employeeCache.put("#evanzhao",
                new EmployeeInfo("evanzhao", "DEV001", "Evan Zhao",
                        "Development", "evanzhao@local.dev", "active"));
        employeeCache.put("#evan.zhao",
                new EmployeeInfo("evan.zhao", "DEV002", "Evan Zhao",
                        "Development", "evan.zhao@local.dev", "active"));

        employeeCache.put("COMPANY#alice.wang",
                new EmployeeInfo("alice.wang", "E10002", "Alice Wang",
                        "HR Department", "alice.wang@company.com", "active"));
        employeeCache.put("COMPANY#leader.wang",
                new EmployeeInfo("leader.wang", "E10004", "Leader Wang",
                        "Management", "leader.wang@company.com", "active"));
        employeeCache.put("COMPANY#pm.li",
                new EmployeeInfo("pm.li", "E10005", "PM Li",
                        "Product Department", "pm.li@company.com", "active"));

        employeeCache.put("COMPANY#bob.li",
                new EmployeeInfo("bob.li", "E10003", "Bob Li",
                        "IT Department", "bob.li@company.com", "inactive"));

        log.info("mock 人员数据初始化完成，共 {} 条记录", employeeCache.size());
    }

    @Override
    public Optional<EmployeeInfo> findByWindowsAccount(String domain, String username) {
        return Optional.ofNullable(employeeCache.get(buildUserKey(domain, username)));
    }

    @Override
    public List<EmployeeInfo> searchMembers(String keyword, int limit) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : "";
        int safeLimit = Math.max(1, Math.min(limit, 20));

        return employeeCache.values().stream()
                .filter(EmployeeInfo::active)
                .filter(employee -> normalizedKeyword.isEmpty() || matches(employee, normalizedKeyword))
                .sorted(Comparator.comparing(EmployeeInfo::displayName))
                .limit(safeLimit)
                .toList();
    }

    private String buildUserKey(String domain, String username) {
        String normalizedDomain = StringUtils.hasText(domain) ? domain.trim().toUpperCase() : "";
        String normalizedUsername = StringUtils.hasText(username) ? username.trim().toLowerCase() : "";
        return normalizedDomain + "#" + normalizedUsername;
    }

    private boolean matches(EmployeeInfo employee, String keyword) {
        return containsIgnoreCase(employee.username(), keyword)
                || containsIgnoreCase(employee.employeeNo(), keyword)
                || containsIgnoreCase(employee.displayName(), keyword)
                || containsIgnoreCase(employee.email(), keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
