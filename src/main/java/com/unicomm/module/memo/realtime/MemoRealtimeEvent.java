package com.unicomm.module.memo.realtime;

import java.time.LocalDateTime;

public record MemoRealtimeEvent(
        String module,
        String type,
        String ownerUsername,
        Long memoId,
        Long groupId,
        String occurredAt) {

    public static MemoRealtimeEvent memo(String type, String ownerUsername, Long memoId, Long groupId) {
        return new MemoRealtimeEvent("memo", type, ownerUsername, memoId, groupId, LocalDateTime.now().toString());
    }

    public static MemoRealtimeEvent group(String type, String ownerUsername, Long groupId) {
        return new MemoRealtimeEvent("memo", type, ownerUsername, null, groupId, LocalDateTime.now().toString());
    }
}
