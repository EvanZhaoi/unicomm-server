package com.unicomm.module.auth.service;

import com.unicomm.common.cache.RedisCacheService;
import com.unicomm.module.auth.dto.DesktopVerifyRequest;
import com.unicomm.module.auth.entity.AuthAuditEntity;
import com.unicomm.module.auth.mapper.AuthAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthAuditService {

    private static final Duration LAST_AUDIT_CACHE_TTL = Duration.ofMinutes(30);

    private final AuthAuditMapper authAuditMapper;
    private final ObjectProvider<RedisCacheService> redisCacheServiceProvider;

    /**
     * 记录认证审计。
     *
     * <p>数据库是审计的主存储，Redis 只缓存最近一次认证结果，方便后续设置页或安全中心
     * 快速展示“最近认证状态”。Redis 未启用或不可用时不影响认证主流程。</p>
     */
    public void record(String username, String action, String result, DesktopVerifyRequest request, String message) {
        AuthAuditEntity entity = new AuthAuditEntity();
        entity.setUsername(username);
        entity.setAction(action);
        entity.setResult(result);
        entity.setDeviceId(request == null ? null : request.getDeviceId());
        entity.setComputerName(request == null ? null : request.getComputerName());
        entity.setMessage(message);
        authAuditMapper.insertAudit(entity);

        if (StringUtils.hasText(username)) {
            redisCacheServiceProvider.ifAvailable(redis -> redis.set(
                    "auth:last-audit:" + username,
                    action + ":" + result,
                    LAST_AUDIT_CACHE_TTL));
        }
    }
}
