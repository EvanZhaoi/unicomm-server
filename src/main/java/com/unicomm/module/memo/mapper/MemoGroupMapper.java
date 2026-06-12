package com.unicomm.module.memo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupResponse;
import com.unicomm.module.memo.entity.MemoGroupEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Memo 分组 Mapper。
 *
 * <p>分组查询经常需要携带该分组下的 Memo 数量，因此这里保留明确 SQL。
 * 服务层只处理权限、默认分组合并和业务编排，不再直接拼接分组表查询。</p>
 */
@Mapper
public interface MemoGroupMapper extends BaseMapper<MemoGroupEntity> {

    @Select("""
            SELECT g.*,
                   (SELECT COUNT(*) FROM uni_memo m
                    WHERE m.owner_username = g.owner_username
                      AND m.group_id = g.id
                      AND m.deleted = 0) AS memo_count
            FROM uni_memo_group g
            WHERE g.owner_username = #{owner} AND g.deleted = 0
            ORDER BY g.sort_order ASC, g.id ASC
            """)
    List<MemoGroupResponse> selectGroupsByOwner(@Param("owner") String owner);

    @Select("""
            SELECT g.*,
                   (SELECT COUNT(*) FROM uni_memo m
                    WHERE m.owner_username = g.owner_username
                      AND m.group_id = g.id
                      AND m.deleted = 0) AS memo_count
            FROM uni_memo_group g
            WHERE g.id = #{id} AND g.owner_username = #{owner} AND g.deleted = 0
            """)
    MemoGroupResponse selectGroupByIdForOwner(@Param("id") Long id, @Param("owner") String owner);

    @Select("""
            SELECT g.*,
                   (SELECT COUNT(*) FROM uni_memo m
                    WHERE m.owner_username = g.owner_username
                      AND m.group_id = g.id
                      AND m.deleted = 0) AS memo_count
            FROM uni_memo_group g
            WHERE g.owner_username = #{owner} AND g.is_default = 1 AND g.deleted = 0
            ORDER BY g.id ASC
            """)
    List<MemoGroupResponse> selectDefaultGroups(@Param("owner") String owner);

    @Select("""
            SELECT COALESCE(MAX(sort_order), 0)
            FROM uni_memo_group
            WHERE owner_username = #{owner} AND deleted = 0
            """)
    Integer selectMaxSortOrder(@Param("owner") String owner);

    @Update("""
            UPDATE uni_memo_group
            SET name = #{name},
                color = #{color},
                icon = #{icon},
                sort_order = COALESCE(#{sortOrder}, sort_order),
                update_time = #{updateTime}
            WHERE id = #{id} AND owner_username = #{owner} AND deleted = 0
            """)
    int updateGroupFields(
            @Param("id") Long id,
            @Param("owner") String owner,
            @Param("name") String name,
            @Param("color") String color,
            @Param("icon") String icon,
            @Param("sortOrder") Integer sortOrder,
            @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            UPDATE uni_memo_group
            SET deleted = 1, update_time = #{updateTime}
            WHERE id = #{id} AND owner_username = #{owner} AND deleted = 0
            """)
    int softDeleteGroup(
            @Param("id") Long id,
            @Param("owner") String owner,
            @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            <script>
            UPDATE uni_memo_group
            SET is_default = 0, deleted = 1, update_time = #{updateTime}
            WHERE owner_username = #{owner}
              AND id IN
              <foreach collection="duplicateIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
              AND deleted = 0
            </script>
            """)
    int softDeleteDuplicateDefaults(
            @Param("owner") String owner,
            @Param("duplicateIds") List<Long> duplicateIds,
            @Param("updateTime") LocalDateTime updateTime);
}
