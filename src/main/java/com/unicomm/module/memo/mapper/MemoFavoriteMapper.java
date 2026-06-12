package com.unicomm.module.memo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unicomm.module.memo.entity.MemoFavoriteEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Memo 收藏 Mapper。
 *
 * <p>收藏使用唯一键 `(memo_id, username)` 做幂等 upsert。
 * 服务层只传入当前用户、Memo 所有人和目标状态，不直接关心表级写入细节。</p>
 */
@Mapper
public interface MemoFavoriteMapper extends BaseMapper<MemoFavoriteEntity> {

    @Insert("""
            INSERT INTO uni_memo_favorite
                (memo_id, username, owner_username, deleted, create_time, update_time)
            VALUES
                (#{memoId}, #{username}, #{ownerUsername}, #{deleted}, #{now}, #{now})
            ON DUPLICATE KEY UPDATE
                deleted = VALUES(deleted),
                owner_username = VALUES(owner_username),
                update_time = VALUES(update_time)
            """)
    int upsertState(
            @Param("memoId") Long memoId,
            @Param("username") String username,
            @Param("ownerUsername") String ownerUsername,
            @Param("deleted") boolean deleted,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*)
            FROM uni_memo_favorite
            WHERE memo_id = #{memoId} AND username = #{username} AND deleted = 0
            """)
    long countActiveByMemoAndUser(@Param("memoId") Long memoId, @Param("username") String username);

    @Update("""
            UPDATE uni_memo_favorite
            SET deleted = 1, update_time = #{now}
            WHERE memo_id = #{memoId} AND owner_username = #{ownerUsername} AND deleted = 0
            """)
    int softDeleteByMemoOwner(
            @Param("memoId") Long memoId,
            @Param("ownerUsername") String ownerUsername,
            @Param("now") LocalDateTime now);
}
