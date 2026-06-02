package com.unicomm.module.memo.realtime;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Memo 实时事件载荷。
 *
 * <p>事件只描述“发生了什么”，不携带完整业务对象。前端收到事件后重新调用 HTTP API 拉取最新数据，
 * 这样可以避免 WebSocket 消息和 REST 响应出现两套数据结构。</p>
 *
 * @param module 模块名，当前固定为 memo
 * @param type 事件类型，如 memo.created、group.updated
 * @param ownerUsername 事件所属用户
 * @param recipientUsernames 本次变更影响的用户列表，包含创建者和相关人
 * @param memoId 变更的 Memo ID，分组事件为空
 * @param groupId 变更影响的分组 ID
 * @param occurredAt 服务端事件生成时间，使用字符串避免额外 Jackson 时间模块配置
 */
public record MemoRealtimeEvent(
        String module,
        String type,
        String ownerUsername,
        List<String> recipientUsernames,
        Long memoId,
        Long groupId,
        String occurredAt) {

    public static MemoRealtimeEvent memo(String type, String ownerUsername, Long memoId, Long groupId) {
        return memo(type, ownerUsername, Set.of(ownerUsername), memoId, groupId);
    }

    public static MemoRealtimeEvent memo(
            String type,
            String ownerUsername,
            Set<String> recipientUsernames,
            Long memoId,
            Long groupId) {
        return new MemoRealtimeEvent(
                "memo",
                type,
                ownerUsername,
                List.copyOf(recipientUsernames),
                memoId,
                groupId,
                LocalDateTime.now().toString());
    }

    public static MemoRealtimeEvent group(String type, String ownerUsername, Long groupId) {
        return new MemoRealtimeEvent(
                "memo",
                type,
                ownerUsername,
                List.of(ownerUsername),
                null,
                groupId,
                LocalDateTime.now().toString());
    }
}
