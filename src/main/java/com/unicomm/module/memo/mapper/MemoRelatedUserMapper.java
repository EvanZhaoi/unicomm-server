package com.unicomm.module.memo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unicomm.module.memo.entity.MemoRelatedUserEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Memo 相关人 Mapper。
 *
 * <p>相关人列表采用整体替换模型：先软删除旧关系，再 upsert 新关系。
 * 这样前端只需要提交完整选择结果，服务端统一负责恢复历史关系或插入新关系。</p>
 */
@Mapper
public interface MemoRelatedUserMapper extends BaseMapper<MemoRelatedUserEntity> {

    @Select("""
            SELECT *
            FROM uni_memo_related_user
            WHERE memo_id = #{memoId} AND deleted = 0
            ORDER BY id ASC
            """)
    List<MemoRelatedUserEntity> selectActiveByMemoId(@Param("memoId") Long memoId);

    @Select("""
            SELECT related_username
            FROM uni_memo_related_user
            WHERE memo_id = #{memoId} AND deleted = 0
            ORDER BY id ASC
            """)
    List<String> selectActiveUsernamesByMemoId(@Param("memoId") Long memoId);

    @Update("""
            UPDATE uni_memo_related_user
            SET deleted = 1, update_time = #{now}
            WHERE memo_id = #{memoId} AND owner_username = #{ownerUsername}
            """)
    int softDeleteByMemoOwner(
            @Param("memoId") Long memoId,
            @Param("ownerUsername") String ownerUsername,
            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO uni_memo_related_user
                (memo_id, owner_username, related_username, permission, deleted, create_time, update_time)
            VALUES
                (#{memoId}, #{ownerUsername}, #{relatedUsername}, #{permission}, 0, #{now}, #{now})
            ON DUPLICATE KEY UPDATE
                permission = VALUES(permission),
                owner_username = VALUES(owner_username),
                deleted = 0,
                update_time = VALUES(update_time)
            """)
    int upsertRelatedUser(
            @Param("memoId") Long memoId,
            @Param("ownerUsername") String ownerUsername,
            @Param("relatedUsername") String relatedUsername,
            @Param("permission") String permission,
            @Param("now") LocalDateTime now);
}
