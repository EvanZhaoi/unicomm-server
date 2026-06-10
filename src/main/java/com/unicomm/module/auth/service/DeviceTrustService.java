package com.unicomm.module.auth.service;

import com.unicomm.common.BusinessException;
import com.unicomm.common.ResultCode;
import com.unicomm.module.auth.dto.DesktopVerifyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTrustService {

    /*
     * 设备信任服务是桌面端认证流程里的“第二道门”。
     *
     * 认证服务先确认 Windows 账号能映射到有效员工，再由这里判断当前设备是否可信：
     * 1. 当前设备已经绑定并处于 trusted 状态：直接放行，并刷新 last_active_time。
     * 2. 用户从未绑定过任何设备：把当前设备作为首台可信设备，直接放行。
     * 3. 用户已有可信设备，但当前设备不一致：生成验证码，等待用户二次确认。
     *
     * 注意：Tauri 应用启动时可能同时存在主窗口、隐藏快速 Memo 窗口或多条恢复请求。
     * 如果不按用户加锁，清空数据库后的首次进入可能出现并发竞态：
     * 第一个请求刚完成首次绑定，第二个请求就把同一轮启动误判为“新设备登录”。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /*
     * 单实例内的用户级锁。当前项目先按单后端部署处理，足以避免同一用户并发认证时
     * 重复生成验证码。后续如果部署多实例，需要升级为数据库唯一约束 + 分布式锁或
     * 基于 INSERT ... ON DUPLICATE KEY 的原子状态流转。
     */
    private final Map<String, Object> userDeviceLocks = new ConcurrentHashMap<>();

    /**
     * 判断当前设备是否需要验证码。
     *
     * @return Optional.empty() 表示设备可信或已完成首次绑定；有值表示需要用户提交验证码。
     */
    public Optional<String> createVerificationIfDeviceUntrusted(String username, String email, DesktopVerifyRequest request) {
        if (!StringUtils.hasText(request.getDeviceId())) {
            return Optional.empty();
        }
        Object lock = userDeviceLocks.computeIfAbsent(username, ignored -> new Object());
        synchronized (lock) {
            if (isTrusted(username, request.getDeviceId())) {
                touchTrustedDevice(username, request);
                return Optional.empty();
            }

            // 首台设备直接绑定，这是测试阶段和企业桌面端首次安装体验的默认策略。
            if (!hasAnyTrustedDevice(username)) {
                trustDevice(username, request);
                log.info("首次绑定设备: username={}, deviceId={}, computerName={}",
                        username, request.getDeviceId(), request.getComputerName());
                return Optional.empty();
            }

            String verificationId = UUID.randomUUID().toString();
            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            MapSqlParameterSource params = baseDeviceParams(username, request)
                    .addValue("verificationId", verificationId)
                    .addValue("codeHash", hashCode(code))
                    .addValue("expireTime", LocalDateTime.now().plusMinutes(10));

            jdbcTemplate.update("""
                    INSERT INTO uni_device_verification
                        (verification_id, username, domain_name, device_id, computer_name, os, os_version,
                         app_version, code_hash, verified, expire_time, create_time, update_time)
                    VALUES
                        (:verificationId, :username, :domain, :deviceId, :computerName, :os, :osVersion,
                         :appVersion, :codeHash, 0, :expireTime, NOW(), NOW())
                    """, params);

            // TODO 接入真实邮件服务后，在这里把 code 发送到 employee email。
            log.info("测试阶段设备验证码: username={}, email={}, deviceId={}, code={}",
                    username, email, request.getDeviceId(), code);
            return Optional.of(verificationId);
        }
    }

    /**
     * 校验设备验证码，并把验证码对应的设备加入可信设备列表。
     *
     * <p>验证码记录中保存了原始桌面认证请求需要的设备字段，所以验证成功后可以还原
     * {@link DesktopVerifyRequest}，让 AuthService 继续走同一套人员校验和 Token 签发逻辑。</p>
     */
    public DesktopVerifyRequest verifyCodeAndTrustDevice(String verificationId, String code) {
        DeviceVerification verification = findVerification(verificationId);
        if (verification == null || verification.verified() || verification.expireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.DEVICE_VERIFICATION_INVALID);
        }
        if (!hashCode(code).equals(verification.codeHash())) {
            throw new BusinessException(ResultCode.DEVICE_VERIFICATION_INVALID, "验证码错误");
        }

        DesktopVerifyRequest request = new DesktopVerifyRequest();
        request.setUsername(verification.username());
        request.setDomain(verification.domain());
        request.setDeviceId(verification.deviceId());
        request.setComputerName(verification.computerName());
        request.setOs(verification.os());
        request.setOsVersion(verification.osVersion());
        request.setAppVersion(verification.appVersion());

        trustDevice(verification.username(), request);
        jdbcTemplate.update("""
                UPDATE uni_device_verification
                SET verified = 1, update_time = NOW()
                WHERE verification_id = :verificationId
                """, new MapSqlParameterSource("verificationId", verificationId));
        return request;
    }

    private boolean isTrusted(String username, String deviceId) {
        // trust_status 保留字符串状态，后续可以扩展 revoked、pending、expired 等设备管理能力。
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM uni_device_trust
                WHERE username = :username
                  AND device_id = :deviceId
                  AND trust_status = 'trusted'
                """, new MapSqlParameterSource()
                .addValue("username", username)
                .addValue("deviceId", deviceId), Integer.class);
        return count != null && count > 0;
    }

    private boolean hasAnyTrustedDevice(String username) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM uni_device_trust
                WHERE username = :username
                  AND trust_status = 'trusted'
                """, new MapSqlParameterSource("username", username), Integer.class);
        return count != null && count > 0;
    }

    private void touchTrustedDevice(String username, DesktopVerifyRequest request) {
        jdbcTemplate.update("""
                UPDATE uni_device_trust
                SET computer_name = :computerName,
                    os = :os,
                    os_version = :osVersion,
                    app_version = :appVersion,
                    last_active_time = NOW(),
                    update_time = NOW()
                WHERE username = :username
                  AND device_id = :deviceId
                """, baseDeviceParams(username, request));
    }

    private void trustDevice(String username, DesktopVerifyRequest request) {
        // 使用唯一键幂等写入，避免重复认证或验证码重复提交造成多条设备记录。
        jdbcTemplate.update("""
                INSERT INTO uni_device_trust
                    (username, device_id, computer_name, os, os_version, app_version, trust_status,
                     first_trusted_time, last_active_time, create_time, update_time)
                VALUES
                    (:username, :deviceId, :computerName, :os, :osVersion, :appVersion, 'trusted',
                     NOW(), NOW(), NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                    computer_name = VALUES(computer_name),
                    os = VALUES(os),
                    os_version = VALUES(os_version),
                    app_version = VALUES(app_version),
                    trust_status = 'trusted',
                    last_active_time = NOW(),
                    update_time = NOW()
                """, baseDeviceParams(username, request));
    }

    private DeviceVerification findVerification(String verificationId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT verification_id, username, domain_name, device_id, computer_name, os, os_version,
                           app_version, code_hash, verified, expire_time
                    FROM uni_device_verification
                    WHERE verification_id = :verificationId
                    """, new MapSqlParameterSource("verificationId", verificationId), (rs, rowNum) ->
                    new DeviceVerification(
                            rs.getString("verification_id"),
                            rs.getString("username"),
                            rs.getString("domain_name"),
                            rs.getString("device_id"),
                            rs.getString("computer_name"),
                            rs.getString("os"),
                            rs.getString("os_version"),
                            rs.getString("app_version"),
                            rs.getString("code_hash"),
                            rs.getBoolean("verified"),
                            rs.getTimestamp("expire_time").toLocalDateTime()));
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private MapSqlParameterSource baseDeviceParams(String username, DesktopVerifyRequest request) {
        return new MapSqlParameterSource()
                .addValue("username", username)
                .addValue("domain", request.getDomain())
                .addValue("deviceId", request.getDeviceId())
                .addValue("computerName", request.getComputerName())
                .addValue("os", request.getOs())
                .addValue("osVersion", request.getOsVersion())
                .addValue("appVersion", request.getAppVersion());
    }

    private String hashCode(String code) {
        // 当前使用 MD5 是测试阶段的轻量实现；生产环境建议改为带 salt 的不可逆 hash。
        return DigestUtils.md5DigestAsHex(code.getBytes(StandardCharsets.UTF_8));
    }

    private record DeviceVerification(
            String verificationId,
            String username,
            String domain,
            String deviceId,
            String computerName,
            String os,
            String osVersion,
            String appVersion,
            String codeHash,
            boolean verified,
            LocalDateTime expireTime) {
    }
}
