package com.unicomm.module.memo.service;

import cn.dev33.satoken.stp.StpUtil;
import com.unicomm.common.BusinessException;
import com.unicomm.common.PageResult;
import com.unicomm.common.ResultCode;
import com.unicomm.module.auth.service.AuthService;
import com.unicomm.module.memo.dto.MemoDtos.BooleanStateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoCreateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupCreateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupResponse;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupUpdateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoRelatedUserRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoRelatedUserResponse;
import com.unicomm.module.memo.dto.MemoDtos.MemoRelatedUsersUpdateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoResponse;
import com.unicomm.module.memo.dto.MemoDtos.MemoUpdateRequest;
import com.unicomm.module.memo.entity.MemoGroupEntity;
import com.unicomm.module.memo.mapper.MemoGroupMapper;
import com.unicomm.module.memo.realtime.MemoRealtimePublisher;
import com.unicomm.module.member.dto.MemberSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JdbcMemoService implements MemoService {

    /*
     * 当前 Memo 模块采用 MyBatis-Plus + NamedParameterJdbcTemplate 渐进式持久化实现。
     *
     * 设计约束：
     * 1. Memo 仍有明确 owner_username；列表和详情同时允许 owner 与相关人查看。
     * 2. 删除采用逻辑删除，避免误删后无法恢复，也便于后续做回收站。
     * 3. 写操作完成后发布 WebSocket 事件，桌面端没有刷新按钮，依赖事件触发重新拉取。
     * 4. 分组等简单 CRUD 已迁入 MyBatis-Plus Mapper；Memo 权限、列表和聚合查询先保留显式 SQL。
     * 5. 收藏/置顶是“用户 + Memo”的个人状态，不能写回 Memo 主表，否则多人共享场景会互相影响。
     * 6. 相关人权限由后端最终判断，前端只负责展示只读/可编辑状态，不能作为安全边界。
     */
    private static final String DEFAULT_GROUP_NAME = "我的备忘";
    private static final String DEFAULT_GROUP_COLOR = "#6B7280";
    private static final String DEFAULT_GROUP_ICON = "folder";
    private static final String DEFAULT_STATUS = "normal";
    private static final String PERMISSION_OWNER = "owner";
    private static final String PERMISSION_EDIT = "edit";
    private static final String PERMISSION_VIEW = "view";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MemoGroupMapper memoGroupMapper;
    private final MemoRealtimePublisher realtimePublisher;
    private final AuthService authService;

    @Override
    public PageResult<MemoResponse> listMemos(
            Integer page,
            Integer size,
            Long groupId,
            String keyword,
            Boolean isShared,
            Boolean isFavorite,
            String status) {

        String owner = currentUsername();
        ensureDefaultGroup(owner);

        // API 参数只作为请求意图，进入 SQL 前统一做边界保护，避免负分页或过大分页拖垮列表查询。
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        int offset = (safePage - 1) * safeSize;

        Map<String, Object> params = new HashMap<>();
        params.put("owner", owner);
        params.put("limit", safeSize);
        params.put("offset", offset);

        // 统一拼接 WHERE，count 查询和列表查询共用同一组过滤条件，避免分页总数和列表内容不一致。
        // 可见范围包含“我创建的”和“别人共享给我的”，这是“全部 Memo”和“与我相关”的共同权限基础。
        StringBuilder where = new StringBuilder("""
                 WHERE m.deleted = 0
                   AND (m.owner_username = :owner
                        OR EXISTS (
                            SELECT 1
                            FROM uni_memo_related_user ru
                            WHERE ru.memo_id = m.id
                              AND ru.related_username = :owner
                              AND ru.deleted = 0
                        ))
                """);
        if (groupId != null) {
            where.append(" AND m.group_id = :groupId");
            params.put("groupId", groupId);
        }
        if (Boolean.TRUE.equals(isShared)) {
            where.append(" AND m.owner_username <> :owner");
        }
        if (isFavorite != null) {
            where.append(isFavorite ? """
                      AND EXISTS (
                          SELECT 1
                          FROM uni_memo_favorite f
                          WHERE f.memo_id = m.id
                            AND f.username = :owner
                            AND f.deleted = 0
                      )
                    """ : """
                      AND NOT EXISTS (
                          SELECT 1
                          FROM uni_memo_favorite f
                          WHERE f.memo_id = m.id
                            AND f.username = :owner
                            AND f.deleted = 0
                      )
                    """);
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND m.status = :status");
            params.put("status", status);
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (LOWER(m.title) LIKE :keyword OR LOWER(m.content) LIKE :keyword)");
            params.put("keyword", "%" + keyword.trim().toLowerCase() + "%");
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM uni_memo m" + where,
                params,
                Long.class);

        // 排序策略：个人置顶优先，其次按最后更新时间倒序，最后用 id 倒序保证相同时间下列表稳定。
        List<MemoResponse> list = jdbcTemplate.query(
                """
                SELECT m.*,
                       g.name AS group_name,
                       EXISTS (
                           SELECT 1
                           FROM uni_memo_top t
                           WHERE t.memo_id = m.id
                             AND t.username = :owner
                             AND t.deleted = 0
                       ) AS is_top,
                       EXISTS (
                           SELECT 1
                           FROM uni_memo_favorite f
                           WHERE f.memo_id = m.id
                             AND f.username = :owner
                             AND f.deleted = 0
                       ) AS is_favorite,
                       CASE
                           WHEN m.owner_username = :owner THEN 'owner'
                           ELSE (
                               SELECT ru.permission
                               FROM uni_memo_related_user ru
                               WHERE ru.memo_id = m.id
                                 AND ru.related_username = :owner
                                 AND ru.deleted = 0
                               ORDER BY ru.id ASC
                               LIMIT 1
                           )
                       END AS current_user_permission
                FROM uni_memo m
                LEFT JOIN uni_memo_group g ON g.id = m.group_id
                """ + where + " ORDER BY is_top DESC, m.update_time DESC, m.id DESC LIMIT :limit OFFSET :offset",
                params,
                memoMapper());

        long safeTotal = total == null ? 0 : total;
        long pages = safeTotal == 0 ? 0 : (safeTotal + safeSize - 1) / safeSize;
        return PageResult.<MemoResponse>builder()
                .list(enrichMemoListResponses(list, owner))
                .total(safeTotal)
                .page(safePage)
                .size(safeSize)
                .pages(pages)
                .build();
    }

    @Override
    public MemoResponse getMemo(Long id) {
        String owner = currentUsername();
        MemoResponse memo = findMemoResponse(id, owner);
        if (memo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Memo 不存在");
        }
        return memo;
    }

    @Override
    @Transactional
    public MemoResponse createMemo(MemoCreateRequest request) {
        String owner = currentUsername();
        MemoGroupResponse defaultGroup = ensureDefaultGroup(owner);
        Long groupId = request.getGroupId() == null ? defaultGroup.getId() : request.getGroupId();

        // group_id 是跨表引用，写入前必须确认该分组属于当前用户，不能让客户端传入别人的分组 ID。
        requireGroupForOwner(groupId, owner);

        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                """
                INSERT INTO uni_memo
                    (owner_username, title, content, group_id, status, deleted, create_time, update_time, update_username)
                VALUES
                    (:owner, :title, :content, :groupId, :status, 0, :createTime, :updateTime, :updateUsername)
                """,
                new MapSqlParameterSource()
                        .addValue("owner", owner)
                        .addValue("title", normalizeTitle(request.getTitle()))
                        .addValue("content", request.getContent() == null ? "" : request.getContent())
                        .addValue("groupId", groupId)
                        .addValue("status", normalizeStatus(request.getStatus()))
                        .addValue("createTime", now)
                        .addValue("updateTime", now)
                        .addValue("updateUsername", owner),
                keyHolder,
                new String[]{"id"});

        MemoResponse memo = getMemo(requiredKey(keyHolder));
        replaceRelatedUsers(memo.getId(), owner, relatedUserRequests(request.getRelatedUsers(), request.getRelatedUsernames()));
        memo = getMemo(memo.getId());

        // 创建 Memo 会影响列表和分组计数，所以同时广播 memo.created 和 group.updated。
        // WebSocket 只广播变化事件和摘要，前端收到后重新拉取 HTTP 列表，避免形成第二套数据查询通道。
        publishMemoNotification(owner, memoRecipients(memo.getId()), "memo.created", memo);
        realtimePublisher.publishGroupChanged(owner, "group.updated", memo.getGroupId());
        return memo;
    }

    @Override
    @Transactional
    public MemoResponse updateMemo(Long id, MemoUpdateRequest request) {
        String owner = currentUsername();
        boolean ownerCanManage = isMemoOwner(id, owner);
        requireMemoCanEdit(id, owner);
        Set<String> recipients = memoRecipients(id);
        if (request.getGroupId() != null) {
            // 分组为空表示不调整分组；分组不为空时仍然必须校验归属。
            // 相关人即便有 edit 权限，也不能把共享 Memo 移入自己的分组，否则会破坏创建人的分类体系。
            if (!ownerCanManage) {
                throw new BusinessException(ResultCode.PERMISSION_DENIED, "只有 Memo 创建人可以调整分组");
            }
            requireGroupForOwner(request.getGroupId(), owner);
        }

        jdbcTemplate.update(
                """
                UPDATE uni_memo
                SET title = :title,
                    content = :content,
                    group_id = CASE WHEN :canManage = 1 THEN COALESCE(:groupId, group_id) ELSE group_id END,
                    status = :status,
                    update_time = :updateTime,
                    update_username = :updateUsername
                WHERE id = :id AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("owner", owner)
                        .addValue("canManage", ownerCanManage ? 1 : 0)
                        .addValue("title", normalizeTitle(request.getTitle()))
                        .addValue("content", request.getContent() == null ? "" : request.getContent())
                        .addValue("groupId", request.getGroupId())
                        .addValue("status", normalizeStatus(request.getStatus()))
                        .addValue("updateTime", LocalDateTime.now())
                        .addValue("updateUsername", owner));

        if (request.getRelatedUsers() != null || request.getRelatedUsernames() != null) {
            if (!ownerCanManage) {
                throw new BusinessException(ResultCode.PERMISSION_DENIED, "只有 Memo 创建人可以调整相关人");
            }
            // 相关人列表采用整体替换模型，前端一次提交完整选择结果，服务端负责去重和过滤创建人自己。
            replaceRelatedUsers(id, owner, relatedUserRequests(request.getRelatedUsers(), request.getRelatedUsernames()));
            recipients.addAll(memoRecipients(id));
        }
        MemoResponse memo = getMemo(id);
        publishMemoNotification(owner, recipients, "memo.updated", memo);
        return memo;
    }

    @Override
    @Transactional
    public MemoResponse updateRelatedUsers(Long id, MemoRelatedUsersUpdateRequest request) {
        String owner = currentUsername();
        requireMemoForOwner(id, owner);
        Set<String> recipients = memoRecipients(id);
        replaceRelatedUsers(
                id,
                owner,
                request == null ? null : relatedUserRequests(request.getRelatedUsers(), request.getRelatedUsernames()));
        touchMemo(id, owner);
        recipients.addAll(memoRecipients(id));
        MemoResponse memo = getMemo(id);
        publishMemoNotification(owner, recipients, "memo.related.updated", memo);
        return memo;
    }

    @Override
    @Transactional
    public void deleteMemo(Long id) {
        String owner = currentUsername();
        requireMemoForOwner(id, owner);
        MemoResponse memo = getMemo(id);
        Set<String> recipients = memoRecipients(id);
        jdbcTemplate.update(
                """
                UPDATE uni_memo
                SET deleted = 1,
                    deleted_time = :deletedTime,
                    update_time = :updateTime,
                    update_username = :updateUsername
                WHERE id = :id AND owner_username = :owner AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("owner", owner)
                        .addValue("deletedTime", LocalDateTime.now())
                        .addValue("updateTime", LocalDateTime.now())
                        .addValue("updateUsername", owner));
        jdbcTemplate.update(
                """
                UPDATE uni_memo_related_user
                SET deleted = 1, update_time = :updateTime
                WHERE memo_id = :id AND owner_username = :owner AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("owner", owner)
                        .addValue("updateTime", LocalDateTime.now()));
        jdbcTemplate.update(
                """
                UPDATE uni_memo_favorite
                SET deleted = 1, update_time = :updateTime
                WHERE memo_id = :id AND owner_username = :owner AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("owner", owner)
                        .addValue("updateTime", LocalDateTime.now()));
        jdbcTemplate.update(
                """
                UPDATE uni_memo_top
                SET deleted = 1, update_time = :updateTime
                WHERE memo_id = :id AND owner_username = :owner AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("owner", owner)
                        .addValue("updateTime", LocalDateTime.now()));
        publishMemoNotification(owner, recipients, "memo.deleted", memo);
        realtimePublisher.publishGroupChanged(owner, "group.updated", null);
    }

    @Override
    public MemoResponse updateTop(Long id, BooleanStateRequest request) {
        String username = currentUsername();
        MemoResponse memo = findMemoResponse(id, username);
        if (memo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Memo 不存在");
        }

        boolean value = Boolean.TRUE.equals(request.getValue());
        LocalDateTime now = LocalDateTime.now();
        // 置顶是个人视图状态。即使 Memo 是共享的，也只影响当前用户自己的列表排序。
        jdbcTemplate.update(
                """
                INSERT INTO uni_memo_top
                    (memo_id, username, owner_username, deleted, create_time, update_time)
                VALUES
                    (:memoId, :username, :ownerUsername, :deleted, :createTime, :updateTime)
                ON DUPLICATE KEY UPDATE
                    deleted = VALUES(deleted),
                    owner_username = VALUES(owner_username),
                    update_time = VALUES(update_time)
                """,
                new MapSqlParameterSource()
                        .addValue("memoId", id)
                        .addValue("username", username)
                        .addValue("ownerUsername", memo.getOwnerUsername())
                        .addValue("deleted", value ? 0 : 1)
                        .addValue("createTime", now)
                        .addValue("updateTime", now));
        return getMemo(id);
    }

    @Override
    public MemoResponse updateFavorite(Long id, BooleanStateRequest request) {
        String username = currentUsername();
        MemoResponse memo = findMemoResponse(id, username);
        if (memo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Memo 不存在");
        }

        boolean value = Boolean.TRUE.equals(request.getValue());
        LocalDateTime now = LocalDateTime.now();
        // 收藏是个人视图状态。共享 Memo 被某人收藏，不应影响创建人或其他相关人的收藏列表。
        jdbcTemplate.update(
                """
                INSERT INTO uni_memo_favorite
                    (memo_id, username, owner_username, deleted, create_time, update_time)
                VALUES
                    (:memoId, :username, :ownerUsername, :deleted, :createTime, :updateTime)
                ON DUPLICATE KEY UPDATE
                    deleted = VALUES(deleted),
                    owner_username = VALUES(owner_username),
                    update_time = VALUES(update_time)
                """,
                new MapSqlParameterSource()
                        .addValue("memoId", id)
                        .addValue("username", username)
                        .addValue("ownerUsername", memo.getOwnerUsername())
                        .addValue("deleted", value ? 0 : 1)
                        .addValue("createTime", now)
                        .addValue("updateTime", now));
        return getMemo(id);
    }

    @Override
    public List<MemoGroupResponse> listGroups() {
        String owner = currentUsername();
        ensureDefaultGroup(owner);
        return memoGroupMapper.selectGroupsByOwner(owner);
    }

    @Override
    @Transactional
    public MemoGroupResponse createGroup(MemoGroupCreateRequest request) {
        String owner = currentUsername();
        ensureDefaultGroup(owner);
        LocalDateTime now = LocalDateTime.now();

        MemoGroupEntity entity = new MemoGroupEntity();
        entity.setOwnerUsername(owner);
        entity.setName(request.getName().trim());
        entity.setColor(normalizeColor(request.getColor()));
        entity.setIcon(normalizeIcon(request.getIcon()));
        entity.setSortOrder(nextSortOrder(owner));
        entity.setIsDefault(false);
        entity.setDeleted(false);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        memoGroupMapper.insert(entity);

        MemoGroupResponse group = requireGroupForOwner(entity.getId(), owner);
        realtimePublisher.publishGroupChanged(owner, "group.created", group.getId());
        return group;
    }

    @Override
    @Transactional
    public MemoGroupResponse updateGroup(Long id, MemoGroupUpdateRequest request) {
        String owner = currentUsername();
        requireGroupForOwner(id, owner);
        memoGroupMapper.updateGroupFields(
                id,
                owner,
                request.getName().trim(),
                normalizeColor(request.getColor()),
                normalizeIcon(request.getIcon()),
                request.getSortOrder(),
                LocalDateTime.now());

        MemoGroupResponse group = requireGroupForOwner(id, owner);
        realtimePublisher.publishGroupChanged(owner, "group.updated", group.getId());
        return group;
    }

    @Override
    @Transactional
    public void deleteGroup(Long id) {
        String owner = currentUsername();
        MemoGroupResponse group = requireGroupForOwner(id, owner);
        if (Boolean.TRUE.equals(group.getIsDefault())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "默认分组不可删除");
        }

        MemoGroupResponse defaultGroup = ensureDefaultGroup(owner);

        // 删除分组前先把该分组下的 Memo 移回默认分组，避免出现悬空 group_id。
        jdbcTemplate.update(
                """
                UPDATE uni_memo
                SET group_id = :defaultGroupId, update_time = :updateTime
                WHERE owner_username = :owner AND group_id = :groupId AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("defaultGroupId", defaultGroup.getId())
                        .addValue("updateTime", LocalDateTime.now())
                        .addValue("owner", owner)
                        .addValue("groupId", group.getId()));

        memoGroupMapper.softDeleteGroup(id, owner, LocalDateTime.now());
        realtimePublisher.publishGroupChanged(owner, "group.deleted", id);
    }

    private MemoResponse findMemoResponse(Long id, String owner) {
        List<MemoResponse> list = jdbcTemplate.query(
                """
                SELECT m.*,
                       g.name AS group_name,
                       EXISTS (
                           SELECT 1
                           FROM uni_memo_top t
                           WHERE t.memo_id = m.id
                             AND t.username = :owner
                             AND t.deleted = 0
                       ) AS is_top
                FROM uni_memo m
                LEFT JOIN uni_memo_group g ON g.id = m.group_id
                WHERE m.id = :id
                  AND m.deleted = 0
                  AND (m.owner_username = :owner
                       OR EXISTS (
                           SELECT 1
                           FROM uni_memo_related_user ru
                           WHERE ru.memo_id = m.id
                             AND ru.related_username = :owner
                             AND ru.deleted = 0
                       ))
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("owner", owner),
                memoMapper());
        return list.isEmpty() ? null : enrichMemoResponse(list.getFirst(), owner);
    }

    private void requireMemoForOwner(Long id, String owner) {
        if (id == null || !existsMemoForOwner(id, owner)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Memo 不存在");
        }
    }

    private void requireMemoCanEdit(Long id, String username) {
        String permission = memoPermission(id, username);
        if (permission == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Memo 不存在");
        }
        if (!PERMISSION_OWNER.equals(permission) && !PERMISSION_EDIT.equals(permission)) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "当前用户没有编辑该 Memo 的权限");
        }
    }

    private boolean existsMemoForOwner(Long id, String owner) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM uni_memo
                WHERE id = :id AND owner_username = :owner AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("owner", owner),
                Long.class);
        return count != null && count > 0;
    }

    private boolean isMemoOwner(Long id, String username) {
        return PERMISSION_OWNER.equals(memoPermission(id, username));
    }

    private String memoPermission(Long id, String username) {
        if (id == null || !StringUtils.hasText(username)) {
            return null;
        }

        /*
         * 权限优先级：
         * 1. 创建人固定拥有 owner 权限。
         * 2. 相关人只能拥有 view/edit 权限。
         * 3. 如果同一用户意外同时出现在两个来源，owner 永远优先。
         */
        List<String> permissions = jdbcTemplate.queryForList(
                """
                SELECT permission
                FROM (
                    SELECT :ownerPermission AS permission, 0 AS sort_order
                    FROM uni_memo m
                    WHERE m.id = :id
                      AND m.owner_username = :username
                      AND m.deleted = 0
                    UNION ALL
                    SELECT ru.permission AS permission, 1 AS sort_order
                    FROM uni_memo m
                    JOIN uni_memo_related_user ru ON ru.memo_id = m.id
                    WHERE m.id = :id
                      AND m.deleted = 0
                      AND ru.related_username = :username
                      AND ru.deleted = 0
                ) p
                ORDER BY sort_order ASC
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("username", username)
                        .addValue("ownerPermission", PERMISSION_OWNER),
                String.class);
        return permissions.isEmpty() ? null : permissions.getFirst();
    }

    private MemoGroupResponse requireGroupForOwner(Long groupId, String owner) {
        if (groupId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分组 ID 不能为空");
        }

        MemoGroupResponse group = memoGroupMapper.selectGroupByIdForOwner(groupId, owner);
        if (group == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分组不存在");
        }
        return group;
    }

    private synchronized MemoGroupResponse ensureDefaultGroup(String owner) {
        // 默认分组是用户首次使用 Memo 的基础数据。这里采用惰性创建，避免额外初始化流程。
        List<MemoGroupResponse> exists = memoGroupMapper.selectDefaultGroups(owner);

        if (!exists.isEmpty()) {
            MemoGroupResponse defaultGroup = exists.getFirst();
            if (exists.size() > 1) {
                mergeDuplicateDefaultGroups(owner, defaultGroup, exists.subList(1, exists.size()));
                return requireGroupForOwner(defaultGroup.getId(), owner);
            }
            return defaultGroup;
        }

        LocalDateTime now = LocalDateTime.now();
        MemoGroupEntity entity = new MemoGroupEntity();
        entity.setOwnerUsername(owner);
        entity.setName(DEFAULT_GROUP_NAME);
        entity.setColor(DEFAULT_GROUP_COLOR);
        entity.setIcon(DEFAULT_GROUP_ICON);
        entity.setSortOrder(0);
        entity.setIsDefault(true);
        entity.setDeleted(false);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        memoGroupMapper.insert(entity);

        return requireGroupForOwner(entity.getId(), owner);
    }

    private void mergeDuplicateDefaultGroups(String owner, MemoGroupResponse defaultGroup, List<MemoGroupResponse> duplicates) {
        // 历史版本或并发首次进入可能留下多个 is_default=1 的分组。
        // 保留最早创建的默认分组，并把重复默认分组下的 Memo 迁回它，避免用户初次进入看到两个默认分组。
        List<Long> duplicateIds = new ArrayList<>();
        for (MemoGroupResponse duplicate : duplicates) {
            duplicateIds.add(duplicate.getId());
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                UPDATE uni_memo
                SET group_id = :defaultGroupId, update_time = :updateTime
                WHERE owner_username = :owner AND group_id IN (:duplicateIds) AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("defaultGroupId", defaultGroup.getId())
                        .addValue("updateTime", now)
                        .addValue("owner", owner)
                        .addValue("duplicateIds", duplicateIds));

        memoGroupMapper.softDeleteDuplicateDefaults(owner, duplicateIds, now);
    }

    private int nextSortOrder(String owner) {
        // sort_order 按 10 递增，后续如果支持拖拽排序，可以在两个相邻值中插入新值。
        Integer maxSort = memoGroupMapper.selectMaxSortOrder(owner);
        return (maxSort == null ? 0 : maxSort) + 10;
    }

    private void replaceRelatedUsers(Long memoId, String owner, List<MemoRelatedUserRequest> relatedUsers) {
        Map<String, String> normalized = normalizeRelatedUsers(relatedUsers, owner);
        LocalDateTime now = LocalDateTime.now();

        // 先软删除当前关系，再按最新名单恢复或插入。这样客户端只需要提交完整相关人列表。
        jdbcTemplate.update(
                """
                UPDATE uni_memo_related_user
                SET deleted = 1, update_time = :updateTime
                WHERE memo_id = :memoId AND owner_username = :owner
                """,
                new MapSqlParameterSource()
                        .addValue("memoId", memoId)
                        .addValue("owner", owner)
                        .addValue("updateTime", now));

        for (Map.Entry<String, String> relatedUser : normalized.entrySet()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO uni_memo_related_user
                        (memo_id, owner_username, related_username, permission, deleted, create_time, update_time)
                    VALUES
                        (:memoId, :owner, :relatedUsername, :permission, 0, :createTime, :updateTime)
                    ON DUPLICATE KEY UPDATE
                        permission = VALUES(permission),
                        deleted = 0,
                        update_time = VALUES(update_time)
                    """,
                    new MapSqlParameterSource()
                            .addValue("memoId", memoId)
                            .addValue("owner", owner)
                            .addValue("relatedUsername", relatedUser.getKey())
                            .addValue("permission", relatedUser.getValue())
                            .addValue("createTime", now)
                            .addValue("updateTime", now));
        }
    }

    private List<MemoRelatedUserRequest> relatedUserRequests(
            List<MemoRelatedUserRequest> relatedUsers,
            List<String> relatedUsernames) {
        if (relatedUsers != null) {
            return relatedUsers;
        }
        if (relatedUsernames == null) {
            return null;
        }
        List<MemoRelatedUserRequest> requests = new ArrayList<>(relatedUsernames.size());
        for (String username : relatedUsernames) {
            requests.add(MemoRelatedUserRequest.builder()
                    .username(username)
                    .permission(PERMISSION_VIEW)
                    .build());
        }
        return requests;
    }

    private Map<String, String> normalizeRelatedUsers(List<MemoRelatedUserRequest> relatedUsers, String owner) {
        Map<String, String> normalized = new java.util.LinkedHashMap<>();
        if (relatedUsers == null) {
            return normalized;
        }

        for (MemoRelatedUserRequest relatedUser : relatedUsers) {
            if (relatedUser == null) {
                continue;
            }
            String username = relatedUser.getUsername();
            if (!StringUtils.hasText(username)) {
                continue;
            }
            String value = username.trim();
            if (!value.equals(owner)) {
                normalized.put(value, normalizeRelatedPermission(relatedUser.getPermission()));
            }
        }
        return normalized;
    }

    private String normalizeRelatedPermission(String permission) {
        if (!StringUtils.hasText(permission)) {
            return PERMISSION_VIEW;
        }
        return switch (permission.trim()) {
            case PERMISSION_EDIT -> PERMISSION_EDIT;
            case PERMISSION_VIEW -> PERMISSION_VIEW;
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "相关人权限不合法");
        };
    }

    private Set<String> memoRecipients(Long memoId) {
        // 接收人包含创建人和所有相关人；前端收到事件后会过滤自己触发的通知。
        Set<String> recipients = new LinkedHashSet<>();
        String memoOwner = memoOwnerUsername(memoId);
        if (StringUtils.hasText(memoOwner)) {
            recipients.add(memoOwner);
        }
        recipients.addAll(relatedUsernames(memoId));
        return recipients;
    }

    private String memoOwnerUsername(Long memoId) {
        List<String> owners = jdbcTemplate.queryForList(
                """
                SELECT owner_username
                FROM uni_memo
                WHERE id = :memoId AND deleted = 0
                LIMIT 1
                """,
                Map.of("memoId", memoId),
                String.class);
        return owners.isEmpty() ? null : owners.getFirst();
    }

    private List<String> relatedUsernames(Long memoId) {
        return jdbcTemplate.queryForList(
                """
                SELECT related_username
                FROM uni_memo_related_user
                WHERE memo_id = :memoId AND deleted = 0
                ORDER BY id ASC
                """,
                Map.of("memoId", memoId),
                String.class);
    }

    private boolean isFavorite(Long memoId, String username) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM uni_memo_favorite
                WHERE memo_id = :memoId AND username = :username AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("memoId", memoId)
                        .addValue("username", username),
                Long.class);
        return count != null && count > 0;
    }

    private MemoResponse enrichMemoResponse(MemoResponse memo, String currentUsername) {
        if (memo == null) {
            return null;
        }

        List<MemoRelatedUserResponse> relatedUsers = listRelatedUsers(memo.getId());
        boolean isOwner = memo.getOwnerUsername().equals(currentUsername);
        memo.setIsOwner(isOwner);
        memo.setIsShared(!isOwner);
        memo.setCurrentUserPermission(isOwner ? PERMISSION_OWNER : memoPermission(memo.getId(), currentUsername));
        memo.setRelatedUsers(relatedUsers);
        memo.setIsFavorite(isFavorite(memo.getId(), currentUsername));
        memo.setUpdateDisplayName(displayName(memo.getUpdateUsername()));
        return memo;
    }

    private List<MemoResponse> enrichMemoResponses(List<MemoResponse> memos, String currentUsername) {
        List<MemoResponse> enriched = new ArrayList<>(memos.size());
        for (MemoResponse memo : memos) {
            enriched.add(enrichMemoResponse(memo, currentUsername));
        }
        return enriched;
    }

    private List<MemoResponse> enrichMemoListResponses(List<MemoResponse> memos, String currentUsername) {
        /*
         * 列表接口保持轻量：不加载 relatedUsers 明细，避免每页列表产生 N 次人员查询。
         * 右侧详情打开时再加载完整相关人，列表滚动和分页会更稳。
         */
        for (MemoResponse memo : memos) {
            boolean isOwner = memo.getOwnerUsername().equals(currentUsername);
            memo.setIsOwner(isOwner);
            memo.setIsShared(!isOwner);
            if (!StringUtils.hasText(memo.getCurrentUserPermission())) {
                memo.setCurrentUserPermission(isOwner ? PERMISSION_OWNER : PERMISSION_VIEW);
            }
            memo.setRelatedUsers(List.of());
        }
        return memos;
    }

    private void touchMemo(Long id, String updateUsername) {
        jdbcTemplate.update(
                """
                UPDATE uni_memo
                SET update_time = :updateTime,
                    update_username = :updateUsername
                WHERE id = :id AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("updateTime", LocalDateTime.now())
                        .addValue("updateUsername", updateUsername));
    }

    private void publishMemoNotification(String actorUsername, Set<String> recipients, String type, MemoResponse memo) {
        // 通知摘要在服务端生成，桌面端弹系统通知时不需要再为了标题/更新人/摘要查详情。
        realtimePublisher.publishMemoChanged(
                actorUsername,
                recipients,
                type,
                memo.getId(),
                memo.getGroupId(),
                memo.getTitle(),
                displayName(actorUsername),
                contentPreview(memo.getContent()));
    }

    private String displayName(String username) {
        if (!StringUtils.hasText(username)) {
            return "";
        }
        MemberSearchResponse member = findMember(username);
        return member == null || !StringUtils.hasText(member.getDisplayName()) ? username : member.getDisplayName();
    }

    private String contentPreview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("[#>*_`\\[\\]()>\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120) + "...";
    }

    private List<MemoRelatedUserResponse> listRelatedUsers(Long memoId) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM uni_memo_related_user
                WHERE memo_id = :memoId AND deleted = 0
                ORDER BY id ASC
                """,
                Map.of("memoId", memoId),
                (rs, rowNum) -> {
                    String username = rs.getString("related_username");
                    MemberSearchResponse member = findMember(username);
                    return MemoRelatedUserResponse.builder()
                            .id(rs.getLong("id"))
                            .username(username)
                            .employeeNo(member == null ? null : member.getEmployeeNo())
                            .displayName(member == null ? null : member.getDisplayName())
                            .departmentName(member == null ? null : member.getDepartmentName())
                            .email(member == null ? null : member.getEmail())
                            .permission(rs.getString("permission"))
                            .createTime(toLocalDateTime(rs, "create_time"))
                            .updateTime(toLocalDateTime(rs, "update_time"))
                            .build();
                });
    }

    private MemberSearchResponse findMember(String username) {
        return authService.searchMembers(username, 20).stream()
                .filter(member -> member.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        return title.trim();
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return DEFAULT_STATUS;
        }
        return switch (status) {
            case "normal", "todo", "done" -> status;
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "Memo 状态不合法");
        };
    }

    private String normalizeColor(String color) {
        return StringUtils.hasText(color) ? color.trim() : DEFAULT_GROUP_COLOR;
    }

    private String normalizeIcon(String icon) {
        return StringUtils.hasText(icon) ? icon.trim() : DEFAULT_GROUP_ICON;
    }

    private String currentUsername() {
        StpUtil.checkLogin();
        return StpUtil.getLoginIdAsString();
    }

    private long requiredKey(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "数据库未返回主键");
        }
        return key.longValue();
    }

    private RowMapper<MemoResponse> memoMapper() {
        // RowMapper 是数据库行到 API 响应 DTO 的唯一转换点，避免 SQL 字段名泄漏到 controller。
        return (rs, rowNum) -> MemoResponse.builder()
                .id(rs.getLong("id"))
                .ownerUsername(rs.getString("owner_username"))
                .title(rs.getString("title"))
                .content(rs.getString("content"))
                .groupId(rs.getLong("group_id"))
                .groupName(rs.getString("group_name"))
                .status(rs.getString("status"))
                .isTop(rs.getBoolean("is_top"))
                .isFavorite(hasColumn(rs, "is_favorite") && rs.getBoolean("is_favorite"))
                .currentUserPermission(hasColumn(rs, "current_user_permission") ? rs.getString("current_user_permission") : null)
                .updateUsername(rs.getString("update_username"))
                .createTime(toLocalDateTime(rs, "create_time"))
                .updateTime(toLocalDateTime(rs, "update_time"))
                .build();
    }

    private boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
        try {
            rs.findColumn(columnName);
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
