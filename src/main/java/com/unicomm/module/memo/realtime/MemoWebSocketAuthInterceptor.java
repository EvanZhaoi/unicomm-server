package com.unicomm.module.memo.realtime;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Memo WebSocket 握手认证拦截器.
 *
 * <p>浏览器原生 WebSocket 不能稳定附加自定义请求头，因此桌面端通过查询参数传递
 * Sa-Token。服务端只在握手时解析 token，并把 loginId 写入 session attributes；
 * 后续广播根据这个 username 做连接级过滤。</p>
 */
@Slf4j
@Component
public class MemoWebSocketAuthInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USERNAME = "username";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            log.warn("Reject WebSocket handshake: missing token");
            return false;
        }

        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null || !StringUtils.hasText(String.valueOf(loginId))) {
                log.warn("Reject WebSocket handshake: invalid token");
                return false;
            }
            attributes.put(ATTR_USERNAME, String.valueOf(loginId));
            return true;
        } catch (RuntimeException error) {
            log.warn("Reject WebSocket handshake: token validation failed");
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No-op. 认证结果已经在 beforeHandshake 中决定。
    }

    private String extractToken(ServerHttpRequest request) {
        String headerToken = firstText(
                request.getHeaders().getFirst("unicomm-token"),
                request.getHeaders().getFirst("satoken"),
                bearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)));
        if (StringUtils.hasText(headerToken)) {
            return headerToken;
        }

        String query = request.getURI().getRawQuery();
        if (!StringUtils.hasText(query)) {
            return null;
        }

        for (String pair : query.split("&")) {
            int splitIndex = pair.indexOf('=');
            if (splitIndex <= 0) {
                continue;
            }
            String key = decode(pair.substring(0, splitIndex));
            if ("token".equals(key) || "unicomm-token".equals(key) || "satoken".equals(key)) {
                return decode(pair.substring(splitIndex + 1));
            }
        }
        return null;
    }

    private String bearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String prefix = "Bearer ";
        return authorization.startsWith(prefix) ? authorization.substring(prefix.length()) : null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
