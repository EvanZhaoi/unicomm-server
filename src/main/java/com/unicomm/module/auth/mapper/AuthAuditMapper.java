package com.unicomm.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unicomm.module.auth.entity.AuthAuditEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认证审计 Mapper。
 *
 * <p>当前只需要追加写入，因此保留一条显式 INSERT，把 create_time 固定交给数据库生成。</p>
 */
@Mapper
public interface AuthAuditMapper extends BaseMapper<AuthAuditEntity> {

    @Insert("""
            INSERT INTO uni_auth_audit
                (username, action, result, device_id, computer_name, ip_address, message, create_time)
            VALUES
                (#{username}, #{action}, #{result}, #{deviceId}, #{computerName}, #{ipAddress}, #{message}, NOW())
            """)
    void insertAudit(AuthAuditEntity entity);
}
