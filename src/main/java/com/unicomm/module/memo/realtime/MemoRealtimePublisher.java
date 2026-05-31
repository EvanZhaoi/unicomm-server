package com.unicomm.module.memo.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoRealtimePublisher {

    private final MemoWebSocketHandler webSocketHandler;

    public void publishMemoChanged(String ownerUsername, String type, Long memoId, Long groupId) {
        webSocketHandler.broadcast(MemoRealtimeEvent.memo(type, ownerUsername, memoId, groupId));
    }

    public void publishGroupChanged(String ownerUsername, String type, Long groupId) {
        webSocketHandler.broadcast(MemoRealtimeEvent.group(type, ownerUsername, groupId));
    }
}
