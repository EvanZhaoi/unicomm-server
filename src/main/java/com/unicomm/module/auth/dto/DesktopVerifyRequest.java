package com.unicomm.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 桌面端认证请求 DTO.
 *
 * <p>桌面客户端验证 Windows 用户身份时提交的数据结构。</p>
 *
 * <p><strong>使用场景:</strong></p>
 * <ul>
 *   <li>桌面客户端启动时发送身份认证请求</li>
 *   <li>客户端从操作系统获取当前 Windows 用户信息</li>
 * </ul>
 *
 * <p><strong>字段说明:</strong></p>
 * <ul>
 *   <li>username, domain: 用于定位用户身份 (必填)</li>
 *   <li>computerName, deviceId, os, osVersion, appVersion: 客户端环境信息 (可选)</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see DesktopVerifyResponse
 * @see com.unicomm.module.auth.controller.AuthController#desktopVerify
 */
@Data
@Schema(description = "桌面端认证请求")
public class DesktopVerifyRequest {

    /**
     * Windows 用户名.
     *
     * <p>从 Windows 操作系统获取的当前用户名。</p>
     * <p>示例: "evan.zhao"</p>
     */
    @Schema(description = "Windows 用户名", example = "evan.zhao")
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * Windows 域.
     *
     * <p>用户所属的 Windows 域或工作组名称。</p>
     * <p>示例: "COMPANY", "WORKGROUP"</p>
     */
    @Schema(description = "Windows 域", example = "COMPANY")
    private String domain;

    /**
     * 计算机名称.
     *
     * <p>桌面客户端所在计算机的主机名。</p>
     * <p>示例: "CN-SH-001"</p>
     */
    @Schema(description = "计算机名称", example = "CN-SH-001")
    private String computerName;

    /**
     * 客户端设备 ID.
     *
     * <p>用于唯一标识客户端设备的 UUID 或类似标识。</p>
     */
    @Schema(description = "客户端设备 ID")
    private String deviceId;

    /**
     * 操作系统类型.
     *
     * <p>客户端运行的操作系统类型。</p>
     * <p>示例: "Windows", "Linux", "macOS"</p>
     */
    @Schema(description = "操作系统类型", example = "Windows")
    private String os;

    /**
     * 操作系统版本.
     *
     * <p>客户端操作系统的详细版本信息。</p>
     * <p>示例: "Windows 11", "Windows 10 Pro"</p>
     */
    @Schema(description = "操作系统版本", example = "Windows 11")
    private String osVersion;

    /**
     * 客户端应用版本.
     *
     * <p>桌面应用程序的版本号。</p>
     * <p>示例: "0.1.0"</p>
     */
    @Schema(description = "客户端应用版本", example = "0.1.0")
    private String appVersion;
}
