package com.unicomm.module.memo.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MemoWebSocketHandler extends TextWebSocketHandler {

    /*
     * WebSocket 当前只负责广播服务端事件，不做业务查询。
     *
     * 连接上的客户端会收到 Memo/分组变更事件，桌面端收到事件后重新拉取 Memo 数据。
     * 这种做法比直接推完整 Memo 更稳：服务端事件体小，前端永远以 HTTP 查询结果作为最终状态。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /*
     * ConcurrentHashMap.newKeySet() 能在多个 Tomcat 工作线程同时连接、断开、广播时保持线程安全。
     * 当前项目是单实例内存会话；如果后续后端多实例部署，需要改成 Redis pub/sub 或消息队列转发。
     */
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: sessionId={}, active={}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();

        // 同时兼容纯文本 ping 和 JSON ping，方便浏览器、Node 测试脚本、桌面端服务共用同一心跳协议。
        if ("ping".equalsIgnoreCase(payload) || isJsonPing(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    private boolean isJsonPing(String payload) {
        try {
            Map<?, ?> body = objectMapper.readValue(payload, Map.class);
            return "ping".equalsIgnoreCase(String.valueOf(body.get("type")));
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket disconnected: sessionId={}, status={}, active={}", session.getId(), status, sessions.size());
    }

    public void broadcast(MemoRealtimeEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (IOException error) {
            log.warn("Serialize WebSocket event failed: event={}", event, error);
            return;
        }

        // 广播前清理已经关闭的连接，避免长期运行后 sessions 集合积累无效对象。
        sessions.removeIf(session -> !session.isOpen());
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException error) {
                log.warn("Send WebSocket event failed: sessionId={}, event={}", session.getId(), event, error);
            }
        }
    }
}
