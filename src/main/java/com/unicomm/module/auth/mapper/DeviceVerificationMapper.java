package com.unicomm.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unicomm.module.auth.entity.DeviceVerificationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 设备验证码 Mapper。
 *
 * <p>验证码记录保存一次新设备认证上下文。校验成功后只标记 verified，
 * 不物理删除，便于后续安全审计。</p>
 */
@Mapper
public interface DeviceVerificationMapper extends BaseMapper<DeviceVerificationEntity> {

    @Insert("""
            INSERT INTO uni_device_verification
                (verification_id, username, domain_name, device_id, computer_name, os, os_version,
                 app_version, code_hash, verified, expire_time, create_time, update_time)
            VALUES
                (#{verificationId}, #{username}, #{domainName}, #{deviceId}, #{computerName}, #{os}, #{osVersion},
                 #{appVersion}, #{codeHash}, 0, #{expireTime}, NOW(), NOW())
            """)
    void insertVerification(DeviceVerificationEntity entity);

    @Select("""
            SELECT verification_id, username, domain_name, device_id, computer_name, os, os_version,
                   app_version, code_hash, verified, expire_time
            FROM uni_device_verification
            WHERE verification_id = #{verificationId}
            """)
    DeviceVerificationEntity findByVerificationId(@Param("verificationId") String verificationId);

    @Update("""
            UPDATE uni_device_verification
            SET verified = 1, update_time = NOW()
            WHERE verification_id = #{verificationId}
            """)
    void markVerified(@Param("verificationId") String verificationId);
}
