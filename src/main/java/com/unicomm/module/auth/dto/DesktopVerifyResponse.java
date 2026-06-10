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
 *   <li>用户基本信息 - username, employeeNo, displayName, departmentName, email</li>
 *   <li>权限列表 - 用户被授权的操作列表</li>
 *   <li>访问令牌 - 用于后续请求的身份凭证</li>
 * </ul>
 *
 * <p><strong>重要说明:</strong></p>
 * <ul>
 *   <li>使用 username 而非 userId 作为用户标识</li>
 *   <li>人员真实信息以公司人员接口返回结果为准</li>
 *   <li> Memo 数据通过 owner_username 进行隔离</li>
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
     * Windows 用户名.
     *
     * <p>用于数据隔离和会话关联。</p>
     * <p>示例: "evan.zhao"</p>
     */
    @Schema(description = "Windows 用户名", example = "evan.zhao")
    private String username;

    /**
     * 员工工号.
     *
     * <p>公司内部工号系统中的编号。</p>
     * <p>示例: "E10001"</p>
     */
    @Schema(description = "员工工号", example = "E10001")
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
     * 邮箱.
     *
     * <p>用户的公司邮箱。</p>
     * <p>示例: "evan.zhao@company.com"</p>
     */
    @Schema(description = "邮箱", example = "evan.zhao@company.com")
    private String email;

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

    @Schema(description = "是否需要设备验证码")
    private Boolean deviceVerificationRequired;

    @Schema(description = "设备验证码流程ID")
    private String verificationId;
}
