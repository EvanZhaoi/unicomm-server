package com.unicomm.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户快照实体。
 *
 * <p>测试阶段人员信息来自 mock provider；生产阶段接入企业人员 API 后，
 * 该表用于保存最近一次认证时的人员信息快照，便于审计、展示和离线排查。</p>
 */
@Data
@TableName("uni_user_snapshot")
public class UserSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String employeeNo;
    private String displayName;
    private String departmentName;
    private String email;
    private String sourceSystem;
    private String statusSnapshot;
    private LocalDateTime lastSyncTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
