package com.unicomm.module.auth.service;

import com.unicomm.common.BusinessException;
import com.unicomm.common.ResultCode;
import com.unicomm.module.auth.dto.DesktopVerifyRequest;
import com.unicomm.module.auth.entity.DeviceTrustEntity;
import com.unicomm.module.auth.entity.DeviceVerificationEntity;
import com.unicomm.module.auth.mapper.DeviceTrustMapper;
import com.unicomm.module.auth.mapper.DeviceVerificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final DeviceTrustMapper deviceTrustMapper;
    private final DeviceVerificationMapper deviceVerificationMapper;

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
            deviceVerificationMapper.insertVerification(toVerificationEntity(
                    username,
                    verificationId,
                    hashCode(code),
                    LocalDateTime.now().plusMinutes(10),
                    request));

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
        DeviceVerificationEntity verification = deviceVerificationMapper.findByVerificationId(verificationId);
        if (verification == null
                || Boolean.TRUE.equals(verification.getVerified())
                || verification.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.DEVICE_VERIFICATION_INVALID);
        }
        if (!hashCode(code).equals(verification.getCodeHash())) {
            throw new BusinessException(ResultCode.DEVICE_VERIFICATION_INVALID, "验证码错误");
        }

        DesktopVerifyRequest request = new DesktopVerifyRequest();
        request.setUsername(verification.getUsername());
        request.setDomain(verification.getDomainName());
        request.setDeviceId(verification.getDeviceId());
        request.setComputerName(verification.getComputerName());
        request.setOs(verification.getOs());
        request.setOsVersion(verification.getOsVersion());
        request.setAppVersion(verification.getAppVersion());

        trustDevice(verification.getUsername(), request);
        deviceVerificationMapper.markVerified(verificationId);
        return request;
    }

    private boolean isTrusted(String username, String deviceId) {
        // trust_status 保留字符串状态，后续可以扩展 revoked、pending、expired 等设备管理能力。
        return deviceTrustMapper.countTrusted(username, deviceId) > 0;
    }

    private boolean hasAnyTrustedDevice(String username) {
        return deviceTrustMapper.countAnyTrusted(username) > 0;
    }

    private void touchTrustedDevice(String username, DesktopVerifyRequest request) {
        deviceTrustMapper.touchTrustedDevice(toTrustEntity(username, request));
    }

    private void trustDevice(String username, DesktopVerifyRequest request) {
        // 使用唯一键幂等写入，避免重复认证或验证码重复提交造成多条设备记录。
        deviceTrustMapper.upsertTrustedDevice(toTrustEntity(username, request));
    }

    private DeviceTrustEntity toTrustEntity(String username, DesktopVerifyRequest request) {
        DeviceTrustEntity entity = new DeviceTrustEntity();
        entity.setUsername(username);
        entity.setDeviceId(request.getDeviceId());
        entity.setComputerName(request.getComputerName());
        entity.setOs(request.getOs());
        entity.setOsVersion(request.getOsVersion());
        entity.setAppVersion(request.getAppVersion());
        return entity;
    }

    private DeviceVerificationEntity toVerificationEntity(
            String username,
            String verificationId,
            String codeHash,
            LocalDateTime expireTime,
            DesktopVerifyRequest request) {
        DeviceVerificationEntity entity = new DeviceVerificationEntity();
        entity.setVerificationId(verificationId);
        entity.setUsername(username);
        entity.setDomainName(request.getDomain());
        entity.setDeviceId(request.getDeviceId());
        entity.setComputerName(request.getComputerName());
        entity.setOs(request.getOs());
        entity.setOsVersion(request.getOsVersion());
        entity.setAppVersion(request.getAppVersion());
        entity.setCodeHash(codeHash);
        entity.setExpireTime(expireTime);
        return entity;
    }

    private String hashCode(String code) {
        // 当前使用 MD5 是测试阶段的轻量实现；生产环境建议改为带 salt 的不可逆 hash。
        return DigestUtils.md5DigestAsHex(code.getBytes(StandardCharsets.UTF_8));
    }
}
