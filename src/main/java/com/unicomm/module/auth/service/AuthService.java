package com.unicomm.module.auth.service;

import com.unicomm.module.auth.dto.DesktopVerifyRequest;
import com.unicomm.module.auth.dto.DesktopVerifyResponse;
import com.unicomm.module.member.dto.MemberSearchResponse;

import java.util.List;

/**
 * 认证服务接口.
 *
 * <p>定义桌面端认证的业务接口，用于验证 Windows 用户身份。</p>
 *
 * <p><strong>职责:</strong></p>
 * <ul>
 *   <li>验证 Windows 用户身份</li>
 *   <li>检查用户状态 (启用/禁用)</li>
 *   <li>签发访问令牌</li>
 *   <li>返回用户信息和权限</li>
 * </ul>
 *
 * @author UniComm Team
 * @version 0.1.0
 * @since 0.1.0
 * @see AuthServiceImpl
 * @see DesktopVerifyRequest
 * @see DesktopVerifyResponse
 */
public interface AuthService {

    /**
     * 桌面端 Windows 用户认证.
     *
     * <p>根据 Windows 用户名和域验证用户身份，验证通过后返回用户信息和访问令牌。</p>
     *
     * <p><strong>处理流程:</strong></p>
     * <ol>
     *   <li>根据 domain#username 查找用户</li>
     *   <li>检查用户是否存在</li>
     *   <li>检查用户状态是否为启用 (status=1)</li>
     *   <li>调用 Sa-Token 签发 Token</li>
     *   <li>构建并返回认证响应</li>
     * </ol>
     *
     * <p><strong>异常:</strong></p>
     * <ul>
     *   <li>用户不存在: {@code BusinessException(ResultCode.USER_DISABLED)}</li>
     *   <li>用户已禁用: {@code BusinessException(ResultCode.USER_DISABLED)}</li>
     * </ul>
     *
     * @param request 桌面认证请求，包含 username, domain 等信息
     * @return 认证响应，包含用户信息和 accessToken
     * @throws BusinessException 用户不存在或已被禁用
     * @since 0.1.0
     * @see DesktopVerifyRequest
     * @see DesktopVerifyResponse
     */
    DesktopVerifyResponse desktopVerify(DesktopVerifyRequest request);

    List<MemberSearchResponse> searchMembers(String keyword, Integer limit);
}
