package com.unicomm.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备信任实体。
 *
 * <p>一条记录表示某个用户信任的一台桌面设备。当前通过 username + deviceId
 * 建立唯一关系，后续可扩展 revoked、expired、managed 等设备状态。</p>
 */
@Data
@TableName("uni_device_trust")
public class DeviceTrustEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String deviceId;
    private String computerName;
    private String os;
    private String osVersion;
    private String appVersion;
    private String trustStatus;
    private LocalDateTime firstTrustedTime;
    private LocalDateTime lastActiveTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
