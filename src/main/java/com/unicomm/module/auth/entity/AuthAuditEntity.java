package com.unicomm.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认证审计实体。
 *
 * <p>认证审计属于安全追踪数据，写入频率高、查询模型简单，适合优先迁移到
 * MyBatis-Plus Mapper。后续可基于该实体继续扩展后台审计查询页面。</p>
 */
@Data
@TableName("uni_auth_audit")
public class AuthAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String action;
    private String result;
    private String deviceId;
    private String computerName;
    private String ipAddress;
    private String message;
    private LocalDateTime createTime;
}
