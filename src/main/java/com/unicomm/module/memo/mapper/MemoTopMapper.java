package com.unicomm.module.memo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unicomm.module.memo.entity.MemoTopEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Memo 置顶 Mapper。
 *
 * <p>置顶和收藏一样属于个人状态，使用 upsert 保证重复点击或重试时不会产生重复记录。</p>
 */
@Mapper
public interface MemoTopMapper extends BaseMapper<MemoTopEntity> {

    @Insert("""
            INSERT INTO uni_memo_top
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

    @Update("""
            UPDATE uni_memo_top
            SET deleted = 1, update_time = #{now}
            WHERE memo_id = #{memoId} AND owner_username = #{ownerUsername} AND deleted = 0
            """)
    int softDeleteByMemoOwner(
            @Param("memoId") Long memoId,
            @Param("ownerUsername") String ownerUsername,
            @Param("now") LocalDateTime now);
}
