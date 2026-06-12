package com.unicomm.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备验证码实体。
 *
 * <p>当用户从未信任的新设备进入系统时，后端生成验证码并保存认证请求快照。
 * 验证成功后可以还原原始设备信息并写入设备信任表。</p>
 */
@Data
@TableName("uni_device_verification")
public class DeviceVerificationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String verificationId;
    private String username;
    private String domainName;
    private String deviceId;
    private String computerName;
    private String os;
    private String osVersion;
    private String appVersion;
    private String codeHash;
    private Boolean verified;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
