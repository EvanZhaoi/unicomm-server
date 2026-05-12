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
 * <p>对应数据库中的 uni_user 表.</p>
 *
 * @author UniComm Team
 * @version 0.1.0
 */
@Data
@TableName("uni_user")
@Schema(description = "员工用户实体")
public class UniUser {

    @Schema(description = "用户 ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "员工编号")
    private String employeeNo;

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "部门 ID")
    private Long departmentId;

    @Schema(description = "部门名称")
    private String departmentName;

    @Schema(description = "状态: 0=禁用, 1=启用")
    private Integer status;

    @Schema(description = "Windows 用户名")
    private String windowsUsername;

    @Schema(description = "Windows 域")
    private String windowsDomain;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
