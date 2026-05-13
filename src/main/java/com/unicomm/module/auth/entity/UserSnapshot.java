package com.unicomm.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户快照实体类.
 *
 * <p>对应数据库中的 uni_user_snapshot 表。</p>
 *
 * <p><strong>重要说明：</strong></p>
 * <ul>
 *   <li>本表不是用户主数据表</li>
 *   <li>人员真实信息以公司人员接口返回结果为准</li>
 *   <li>本表仅用于缓存和审计</li>
 * </ul>
 *
 * <p>当后端调用公司人员接口成功后，会将人员信息写入此表作为快照。</p>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 */
@Data
@TableName("uni_user_snapshot")
@Schema(description = "用户快照实体（非主数据，仅缓存和审计）")
public class UserSnapshot {

    /**
     * 主键 ID.
     */
    @Schema(description = "主键 ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Windows 用户名.
     *
     * <p>用于数据隔离和会话关联。</p>
     */
    @Schema(description = "Windows 用户名")
    private String username;

    /**
     * 员工工号.
     */
    @Schema(description = "员工工号")
    private String employeeNo;

    /**
     * 显示名称.
     */
    @Schema(description = "显示名称")
    private String displayName;

    /**
     * 部门名称.
     */
    @Schema(description = "部门名称")
    private String departmentName;

    /**
     * 邮箱.
     */
    @Schema(description = "邮箱")
    private String email;

    /**
     * 来源系统标识.
     *
     * <p>记录人员信息的来源系统，例如：HR_SYSTEM, AD, LDAP 等。</p>
     */
    @Schema(description = "来源系统标识")
    private String sourceSystem;

    /**
     * 状态快照.
     *
     * <p>记录从公司人员接口获取时的状态：active / inactive。</p>
     */
    @Schema(description = "状态快照: active/inactive")
    private String statusSnapshot;

    /**
     * 最后同步时间.
     *
     * <p>记录从公司人员接口同步的时间。</p>
     */
    @Schema(description = "最后同步时间")
    private LocalDateTime lastSyncTime;

    /**
     * 创建时间.
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间.
     */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}