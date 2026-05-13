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
 * <p>桌面端认证成功后返回给客户端的数据结构。</p>
 *
 * <p><strong>响应内容:</strong></p>
 * <ul>
 *   <li>用户基本信息 - id, employeeNo, displayName, departmentName</li>
 *   <li>权限列表 - 用户被授权的操作列表</li>
 *   <li>访问令牌 - 用于后续请求的身份凭证</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see DesktopVerifyRequest
 * @see com.unicomm.module.auth.controller.AuthController#desktopVerify
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "桌面端认证响应")
public class DesktopVerifyResponse {

    /**
     * 用户 ID.
     *
     * <p>用户在 UniComm 系统中的唯一标识。</p>
     */
    @Schema(description = "用户 ID")
    private Long userId;

    /**
     * 员工编号.
     *
     * <p>公司内部工号系统中的编号。</p>
     * <p>示例: "E10001"</p>
     */
    @Schema(description = "员工编号", example = "E10001")
    private String employeeNo;

    /**
     * 显示名称.
     *
     * <p>用户在前端界面中显示的名称。</p>
     * <p>示例: "Evan Zhao"</p>
     */
    @Schema(description = "显示名称", example = "Evan Zhao")
    private String displayName;

    /**
     * 部门名称.
     *
     * <p>用户所属的部门名称。</p>
     * <p>示例: "IT Department"</p>
     */
    @Schema(description = "部门名称", example = "IT Department")
    private String departmentName;

    /**
     * 权限列表.
     *
     * <p>用户被授权的操作权限。</p>
     * <p>示例: ["memo:read", "memo:write"]</p>
     */
    @Schema(description = "权限列表")
    private List<String> permissions;

    /**
     * 访问令牌.
     *
     * <p>由服务端签发的认证令牌，用于后续请求的身份验证。</p>
     * <p>客户端需要在后续请求的 Header 中携带此 Token:</p>
     * <pre>Authorization: Bearer {accessToken}</pre>
     */
    @Schema(description = "访问令牌 (由服务端签发)")
    private String accessToken;
}