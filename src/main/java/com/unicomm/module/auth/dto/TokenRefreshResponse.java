package com.unicomm.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Token 刷新响应")
public class TokenRefreshResponse {

    @Schema(description = "当前访问令牌")
    private String accessToken;

    @Schema(description = "前端本地 Session 建议过期时间，Unix 毫秒")
    private Long expiresAt;
}
