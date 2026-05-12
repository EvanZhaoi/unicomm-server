package com.unicomm.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 桌面端认证请求 DTO.
 *
 * <p>客户端验证 Windows 用户身份时提交的数据.</p>
 *
 * @author UniComm Team
 * @version 0.1.0
 */
@Data
@Schema(description = "桌面端认证请求")
public class DesktopVerifyRequest {

    @Schema(description = "Windows 用户名", example = "evan.zhao")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "Windows 域", example = "COMPANY")
    @NotBlank(message = "域不能为空")
    private String domain;

    @Schema(description = "计算机名称", example = "CN-SH-001")
    private String computerName;

    @Schema(description = "客户端设备 ID")
    private String deviceId;

    @Schema(description = "操作系统类型", example = "Windows")
    private String os;

    @Schema(description = "操作系统版本", example = "Windows 11")
    private String osVersion;

    @Schema(description = "客户端应用版本", example = "0.1.0")
    private String appVersion;
}
