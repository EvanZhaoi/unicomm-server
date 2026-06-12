package com.unicomm.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unicomm.module.auth.entity.DeviceTrustEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 设备信任 Mapper。
 *
 * <p>设备信任涉及幂等 upsert 和时间刷新，使用显式 SQL 保留业务意图。</p>
 */
@Mapper
public interface DeviceTrustMapper extends BaseMapper<DeviceTrustEntity> {

    @Select("""
            SELECT COUNT(*)
            FROM uni_device_trust
            WHERE username = #{username}
              AND device_id = #{deviceId}
              AND trust_status = 'trusted'
            """)
    int countTrusted(@Param("username") String username, @Param("deviceId") String deviceId);

    @Select("""
            SELECT COUNT(*)
            FROM uni_device_trust
            WHERE username = #{username}
              AND trust_status = 'trusted'
            """)
    int countAnyTrusted(@Param("username") String username);

    @Update("""
            UPDATE uni_device_trust
            SET computer_name = #{computerName},
                os = #{os},
                os_version = #{osVersion},
                app_version = #{appVersion},
                last_active_time = NOW(),
                update_time = NOW()
            WHERE username = #{username}
              AND device_id = #{deviceId}
            """)
    void touchTrustedDevice(DeviceTrustEntity entity);

    @Insert("""
            INSERT INTO uni_device_trust
                (username, device_id, computer_name, os, os_version, app_version, trust_status,
                 first_trusted_time, last_active_time, create_time, update_time)
            VALUES
                (#{username}, #{deviceId}, #{computerName}, #{os}, #{osVersion}, #{appVersion}, 'trusted',
                 NOW(), NOW(), NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                computer_name = VALUES(computer_name),
                os = VALUES(os),
                os_version = VALUES(os_version),
                app_version = VALUES(app_version),
                trust_status = 'trusted',
                last_active_time = NOW(),
                update_time = NOW()
            """)
    void upsertTrustedDevice(DeviceTrustEntity entity);
}
