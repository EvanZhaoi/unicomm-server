package com.unicomm.module.memo.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class MemoRealtimePublisher {

    /*
     * 业务服务只依赖这个 Publisher，不直接操作 WebSocket handler。
     * 这样后续如果要把事件投递改成 Redis、MQ 或按用户分组推送，只需要替换这一层。
     */
    private final MemoWebSocketHandler webSocketHandler;

    /**
     * 发布 Memo 级别变更事件。
     *
     * @param ownerUsername 事件所属用户，前端可用于忽略非当前用户事件
     * @param type 事件类型，例如 memo.created、memo.updated、memo.deleted
     * @param memoId 发生变化的 Memo ID
     * @param groupId 变化影响的分组 ID，删除时可能为空
     */
    public void publishMemoChanged(String ownerUsername, String type, Long memoId, Long groupId) {
        webSocketHandler.broadcast(MemoRealtimeEvent.memo(type, ownerUsername, memoId, groupId));
    }

    /**
     * 发布会影响多个用户的 Memo 变更事件。
     *
     * <p>当前 WebSocket handler 仍是单实例广播，recipientUsernames 会进入事件体，
     * 前端可据此判断事件是否和当前用户相关；后续接入用户级会话后，也可以在 handler 层按这个列表分发。</p>
     */
    public void publishMemoChanged(
            String ownerUsername,
            Set<String> recipientUsernames,
            String type,
            Long memoId,
            Long groupId) {
        webSocketHandler.broadcast(MemoRealtimeEvent.memo(type, ownerUsername, recipientUsernames, memoId, groupId));
    }

    /**
     * 发布分组级别变更事件。
     *
     * @param ownerUsername 事件所属用户
     * @param type 事件类型，例如 group.created、group.updated、group.deleted
     * @param groupId 发生变化的分组 ID
     */
    public void publishGroupChanged(String ownerUsername, String type, Long groupId) {
        webSocketHandler.broadcast(MemoRealtimeEvent.group(type, ownerUsername, groupId));
    }
}
