package com.unicomm.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 桌面端认证响应 DTO.
 *
 * <p>验证成功后返回的用户信息和访问令牌.</p>
 *
 * @author UniComm Team
 * @version 0.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "桌面端认证响应")
public class DesktopVerifyResponse {

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "员工编号", example = "E10001")
    private String employeeNo;

    @Schema(description = "显示名称", example = "Evan Zhao")
    private String displayName;

    @Schema(description = "部门名称", example = "IT Department")
    private String departmentName;

    @Schema(description = "权限列表")
    private List<String> permissions;

    @Schema(description = "访问令牌 (由服务端签发)")
    private String accessToken;
}
