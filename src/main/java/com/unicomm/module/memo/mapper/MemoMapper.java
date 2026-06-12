package com.unicomm.module.memo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unicomm.module.memo.dto.MemoDtos.MemoResponse;
import com.unicomm.module.memo.entity.MemoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Memo 主表 Mapper。
 *
 * <p>Memo 列表和权限判断涉及 owner、相关人、收藏、置顶和分组聚合。
 * 这些查询继续使用显式 SQL，但统一收敛在 Mapper 中，Service 只负责业务编排。</p>
 */
@Mapper
public interface MemoMapper extends BaseMapper<MemoEntity> {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM uni_memo m
            WHERE m.deleted = 0
              AND (m.owner_username = #{owner}
                   OR EXISTS (
                       SELECT 1
                       FROM uni_memo_related_user ru
                       WHERE ru.memo_id = m.id
                         AND ru.related_username = #{owner}
                         AND ru.deleted = 0
                   ))
            <if test="groupId != null">
              AND m.group_id = #{groupId}
            </if>
            <if test="isShared == true">
              AND m.owner_username != #{owner}
            </if>
            <if test="isFavorite != null and isFavorite == true">
              AND EXISTS (
                  SELECT 1
                  FROM uni_memo_favorite f
                  WHERE f.memo_id = m.id
                    AND f.username = #{owner}
                    AND f.deleted = 0
              )
            </if>
            <if test="isFavorite != null and isFavorite == false">
              AND NOT EXISTS (
                  SELECT 1
                  FROM uni_memo_favorite f
                  WHERE f.memo_id = m.id
                    AND f.username = #{owner}
                    AND f.deleted = 0
              )
            </if>
            <if test="status != null and status != ''">
              AND m.status = #{status}
            </if>
            <if test="keyword != null and keyword != ''">
              AND (LOWER(m.title) LIKE #{keyword} OR LOWER(m.content) LIKE #{keyword})
            </if>
            </script>
            """)
    long countVisible(
            @Param("owner") String owner,
            @Param("groupId") Long groupId,
            @Param("keyword") String keyword,
            @Param("isShared") Boolean isShared,
            @Param("isFavorite") Boolean isFavorite,
            @Param("status") String status);

    @Select("""
            <script>
            SELECT m.*,
                   g.name AS group_name,
                   EXISTS (
                       SELECT 1
                       FROM uni_memo_top t
                       WHERE t.memo_id = m.id
                         AND t.username = #{owner}
                         AND t.deleted = 0
                   ) AS is_top,
                   EXISTS (
                       SELECT 1
                       FROM uni_memo_favorite f
                       WHERE f.memo_id = m.id
                         AND f.username = #{owner}
                         AND f.deleted = 0
                   ) AS is_favorite,
                   CASE
                       WHEN m.owner_username = #{owner} THEN 'owner'
                       ELSE (
                           SELECT ru.permission
                           FROM uni_memo_related_user ru
                           WHERE ru.memo_id = m.id
                             AND ru.related_username = #{owner}
                             AND ru.deleted = 0
                           ORDER BY ru.id ASC
                           LIMIT 1
                       )
                   END AS current_user_permission
            FROM uni_memo m
            LEFT JOIN uni_memo_group g ON g.id = m.group_id
            WHERE m.deleted = 0
              AND (m.owner_username = #{owner}
                   OR EXISTS (
                       SELECT 1
                       FROM uni_memo_related_user ru
                       WHERE ru.memo_id = m.id
                         AND ru.related_username = #{owner}
                         AND ru.deleted = 0
                   ))
            <if test="groupId != null">
              AND m.group_id = #{groupId}
            </if>
            <if test="isShared == true">
              AND m.owner_username != #{owner}
            </if>
            <if test="isFavorite != null and isFavorite == true">
              AND EXISTS (
                  SELECT 1
                  FROM uni_memo_favorite f
                  WHERE f.memo_id = m.id
                    AND f.username = #{owner}
                    AND f.deleted = 0
              )
            </if>
            <if test="isFavorite != null and isFavorite == false">
              AND NOT EXISTS (
                  SELECT 1
                  FROM uni_memo_favorite f
                  WHERE f.memo_id = m.id
                    AND f.username = #{owner}
                    AND f.deleted = 0
              )
            </if>
            <if test="status != null and status != ''">
              AND m.status = #{status}
            </if>
            <if test="keyword != null and keyword != ''">
              AND (LOWER(m.title) LIKE #{keyword} OR LOWER(m.content) LIKE #{keyword})
            </if>
            ORDER BY is_top DESC, m.update_time DESC, m.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MemoResponse> selectVisiblePage(
            @Param("owner") String owner,
            @Param("groupId") Long groupId,
            @Param("keyword") String keyword,
            @Param("isShared") Boolean isShared,
            @Param("isFavorite") Boolean isFavorite,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            SELECT m.*,
                   g.name AS group_name,
                   EXISTS (
                       SELECT 1
                       FROM uni_memo_top t
                       WHERE t.memo_id = m.id
                         AND t.username = #{owner}
                         AND t.deleted = 0
                   ) AS is_top,
                   EXISTS (
                       SELECT 1
                       FROM uni_memo_favorite f
                       WHERE f.memo_id = m.id
                         AND f.username = #{owner}
                         AND f.deleted = 0
                   ) AS is_favorite
            FROM uni_memo m
            LEFT JOIN uni_memo_group g ON g.id = m.group_id
            WHERE m.id = #{id}
              AND m.deleted = 0
              AND (m.owner_username = #{owner}
                   OR EXISTS (
                       SELECT 1
                       FROM uni_memo_related_user ru
                       WHERE ru.memo_id = m.id
                         AND ru.related_username = #{owner}
                         AND ru.deleted = 0
                   ))
            """)
    MemoResponse selectVisibleById(@Param("id") Long id, @Param("owner") String owner);

    @Select("""
            SELECT COUNT(*)
            FROM uni_memo
            WHERE id = #{id} AND owner_username = #{owner} AND deleted = 0
            """)
    long countActiveByOwner(@Param("id") Long id, @Param("owner") String owner);

    @Select("""
            SELECT permission
            FROM (
                SELECT #{ownerPermission} AS permission, 0 AS sort_order
                FROM uni_memo m
                WHERE m.id = #{id}
                  AND m.owner_username = #{username}
                  AND m.deleted = 0
                UNION ALL
                SELECT ru.permission AS permission, 1 AS sort_order
                FROM uni_memo m
                JOIN uni_memo_related_user ru ON ru.memo_id = m.id
                WHERE m.id = #{id}
                  AND m.deleted = 0
                  AND ru.related_username = #{username}
                  AND ru.deleted = 0
            ) p
            ORDER BY sort_order ASC
            LIMIT 1
            """)
    String selectPermission(
            @Param("id") Long id,
            @Param("username") String username,
            @Param("ownerPermission") String ownerPermission);

    @Select("""
            SELECT owner_username
            FROM uni_memo
            WHERE id = #{memoId} AND deleted = 0
            LIMIT 1
            """)
    String selectOwnerUsername(@Param("memoId") Long memoId);

    @Update("""
            UPDATE uni_memo
            SET title = #{title},
                content = #{content},
                group_id = CASE WHEN #{canManage} = 1 THEN COALESCE(#{groupId}, group_id) ELSE group_id END,
                status = #{status},
                update_time = #{updateTime},
                update_username = #{updateUsername}
            WHERE id = #{id} AND deleted = 0
            """)
    int updateMemoFields(
            @Param("id") Long id,
            @Param("canManage") int canManage,
            @Param("title") String title,
            @Param("content") String content,
            @Param("groupId") Long groupId,
            @Param("status") String status,
            @Param("updateTime") LocalDateTime updateTime,
            @Param("updateUsername") String updateUsername);

    @Update("""
            UPDATE uni_memo
            SET deleted = 1,
                deleted_time = #{deletedTime},
                update_time = #{updateTime},
                update_username = #{updateUsername}
            WHERE id = #{id} AND owner_username = #{owner} AND deleted = 0
            """)
    int softDeleteByOwner(
            @Param("id") Long id,
            @Param("owner") String owner,
            @Param("deletedTime") LocalDateTime deletedTime,
            @Param("updateTime") LocalDateTime updateTime,
            @Param("updateUsername") String updateUsername);

    @Update("""
            UPDATE uni_memo
            SET group_id = #{defaultGroupId}, update_time = #{updateTime}
            WHERE owner_username = #{owner} AND group_id = #{groupId} AND deleted = 0
            """)
    int moveGroupMemosToDefault(
            @Param("owner") String owner,
            @Param("groupId") Long groupId,
            @Param("defaultGroupId") Long defaultGroupId,
            @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            <script>
            UPDATE uni_memo
            SET group_id = #{defaultGroupId}, update_time = #{updateTime}
            WHERE owner_username = #{owner}
              AND group_id IN
              <foreach collection="duplicateIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
              AND deleted = 0
            </script>
            """)
    int moveDuplicateDefaultGroupMemos(
            @Param("owner") String owner,
            @Param("duplicateIds") List<Long> duplicateIds,
            @Param("defaultGroupId") Long defaultGroupId,
            @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            UPDATE uni_memo
            SET update_time = #{updateTime},
                update_username = #{updateUsername}
            WHERE id = #{id} AND deleted = 0
            """)
    int touch(
            @Param("id") Long id,
            @Param("updateUsername") String updateUsername,
            @Param("updateTime") LocalDateTime updateTime);
}
