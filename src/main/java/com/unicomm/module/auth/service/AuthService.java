package com.unicomm.module.auth.service;

import com.unicomm.module.auth.dto.DesktopVerifyRequest;
import com.unicomm.module.auth.dto.DesktopVerifyResponse;

/**
 * 认证服务接口.
 *
 * @author UniComm Team
 * @version 0.1.0
 */
public interface AuthService {

    /**
     * 桌面端 Windows 用户认证.
     *
     * <p>根据 Windows 用户名和域验证用户身份，
     * 验证通过后返回用户信息和访问令牌.</p>
     *
     * @param request 桌面认证请求
     * @return 认证响应 (包含用户信息和 Token)
     */
    DesktopVerifyResponse desktopVerify(DesktopVerifyRequest request);
}
