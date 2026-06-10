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
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTrustService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Optional<String> createVerificationIfDeviceUntrusted(String username, String email, DesktopVerifyRequest request) {
        if (!StringUtils.hasText(request.getDeviceId())) {
            return Optional.empty();
        }
        if (isTrusted(username, request.getDeviceId())) {
            touchTrustedDevice(username, request);
            return Optional.empty();
        }
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
