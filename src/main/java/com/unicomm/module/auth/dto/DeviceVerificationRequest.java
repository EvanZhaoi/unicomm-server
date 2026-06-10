package com.unicomm.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "设备验证码校验请求")
public class DeviceVerificationRequest {

    @NotBlank(message = "验证码流程ID不能为空")
    @Schema(description = "验证码流程ID")
    private String verificationId;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "邮件验证码")
    private String code;
}
