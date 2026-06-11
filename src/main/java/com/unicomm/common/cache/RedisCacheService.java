package com.unicomm.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 缓存服务。
 *
 * <p>该服务只在 `unicomm.redis.enabled=true` 时启用。这样可以先把 Redis 作为企业级缓存
 * 基础设施接进项目，但不强制当前只安装 MySQL 的测试环境必须启动 Redis。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "unicomm.redis", name = "enabled", havingValue = "true")
public class RedisCacheService {

    private final StringRedisTemplate stringRedisTemplate;

    public void set(String key, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (RuntimeException error) {
            // 缓存失败不能阻断主业务，尤其认证和审计必须以数据库落库为准。
            log.warn("Redis cache set failed: key={}", key, error);
        }
    }
}
