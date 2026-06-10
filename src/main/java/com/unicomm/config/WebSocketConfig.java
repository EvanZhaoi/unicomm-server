package com.unicomm.config;

import com.unicomm.module.memo.realtime.MemoWebSocketHandler;
import com.unicomm.module.memo.realtime.MemoWebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MemoWebSocketHandler memoWebSocketHandler;
    private final MemoWebSocketAuthInterceptor memoWebSocketAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(memoWebSocketHandler, "/ws")
                .addInterceptors(memoWebSocketAuthInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
