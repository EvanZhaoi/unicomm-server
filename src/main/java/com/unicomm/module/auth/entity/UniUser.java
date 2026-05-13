package com.unicomm.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工用户实体类.
 *
 * <p>对应数据库中的 uni_user 表，存储系统用户信息。</p>
 *
 * <p><strong>数据库表结构:</strong></p>
 * <ul>
 *   <li>主键: id (自增)</li>
 *   <li>Windows 认证: windowsUsername + windowsDomain 联合唯一</li>
 *   <li>状态: status (0=禁用, 1=启用)</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see com.unicomm.module.auth.service.impl.AuthServiceImpl
 */
@Data
@TableName("uni_user")
@Schema(description = "员工用户实体")
public class UniUser {

    /**
     * 用户 ID.
     *
     * <p>自增主键，用户的唯一标识。</p>
     */
    @Schema(description = "用户 ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工编号.
     *
     * <p>公司内部工号，如 "E10001"。</p>
     */
    @Schema(description = "员工编号")
    private String employeeNo;

    /**
     * 显示名称.
     *
     * <p>用户在前端显示的名称，如 "Evan Zhao"。</p>
     */
    @Schema(description = "显示名称")
    private String displayName;

    /**
     * 部门 ID.
     *
     * <p>用户所属部门的 ID。</p>
     */
    @Schema(description = "部门 ID")
    private Long departmentId;

    /**
     * 部门名称.
     *
     * <p>用户所属部门的名称，如 "IT Department"。</p>
     */
    @Schema(description = "部门名称")
    private String departmentName;

    /**
     * 状态.
     *
     * <p>用户状态:</p>
     * <ul>
     *   <li>0 - 禁用 (无法登录)</li>
     *   <li>1 - 启用 (可以正常登录)</li>
     * </ul>
     */
    @Schema(description = "状态: 0=禁用, 1=启用")
    private Integer status;

    /**
     * Windows 用户名.
     *
     * <p>用户的 Windows 登录用户名，如 "evan.zhao"。</p>
     * <p>与 windowsDomain 联合使用确定 Windows 身份。</p>
     */
    @Schema(description = "Windows 用户名")
    private String windowsUsername;

    /**
     * Windows 域.
     *
     * <p>用户所属的 Windows 域或工作组，如 "COMPANY"。</p>
     * <p>与 windowsUsername 联合使用确定 Windows 身份。</p>
     */
    @Schema(description = "Windows 域")
    private String windowsDomain;

    /**
     * 创建时间.
     *
     * <p>记录首次创建时间。</p>
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间.
     *
     * <p>记录最后修改时间。</p>
     */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}