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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: sessionId={}, active={}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
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
