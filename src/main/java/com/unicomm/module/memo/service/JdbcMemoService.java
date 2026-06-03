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
     * 当前 Memo 模块只保留 MySQL 持久化实现。
     *
     * 设计约束：
     * 1. Memo 仍有明确 owner_username；列表和详情同时允许 owner 与相关人查看。
     * 2. 删除采用逻辑删除，避免误删后无法恢复，也便于后续做回收站。
     * 3. 写操作完成后发布 WebSocket 事件，桌面端没有刷新按钮，依赖事件触发重新拉取。
     * 4. 使用 NamedParameterJdbcTemplate 是为了让 SQL 字段和业务含义更直接，当前阶段不引入 ORM。
     */
    private static final String DEFAULT_GROUP_NAME = "我的备忘";
    private static final String DEFAULT_GROUP_COLOR = "#6B7280";
    private static final String DEFAULT_GROUP_ICON = "folder";
    private static final String DEFAULT_STATUS = "normal";
    private static final String PERMISSION_OWNER = "owner";
    private static final String PERMISSION_EDIT = "edit";
    private static final String PERMISSION_VIEW = "view";

    /*
     * 认证拦截器目前还没有强制保护所有 Memo 接口。保留一个本地兜底用户，
     * 让接口文档和本机联调在没有 Token 的情况下仍可跑通。
     * 等后续开启全局鉴权后，这个兜底值应移除。
     */
    private static final String DEV_FALLBACK_USERNAME = "evan.zhao";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MemoRealtimePublisher realtimePublisher;
    private final AuthService authService;

    @Override
    public PageResult<MemoResponse> listMemos(
            Integer page,
            Integer size,
            Long groupId,
            String keyword,
            Boolean isArchived,
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
        if (isArchived != null) {
            where.append(" AND m.is_archived = :isArchived");
            params.put("isArchived", isArchived ? 1 : 0);
        }
        if (isFavorite != null) {
            where.append(" AND m.is_favorite = :isFavorite");
            params.put("isFavorite", isFavorite ? 1 : 0);
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

        List<MemoResponse> list = jdbcTemplate.query(
                """
                SELECT m.*, g.name AS group_name
                FROM uni_memo m
                LEFT JOIN uni_memo_group g ON g.id = m.group_id
                """ + where + " ORDER BY m.is_top DESC, m.update_time DESC LIMIT :limit OFFSET :offset",
                params,
                memoMapper());

        long safeTotal = total == null ? 0 : total;
        long pages = safeTotal == 0 ? 0 : (safeTotal + safeSize - 1) / safeSize;
        return PageResult.<MemoResponse>builder()
                .list(enrichMemoResponses(list, owner))
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
                    (owner_username, title, content, group_id, status, is_top, is_favorite,
                     is_archived, deleted, create_time, update_time)
                VALUES
                    (:owner, :title, :content, :groupId, :status, 0, 0, 0, 0, :createTime, :updateTime)
                """,
                new MapSqlParameterSource()
                        .addValue("owner", owner)
                        .addValue("title", normalizeTitle(request.getTitle()))
                        .addValue("content", request.getContent() == null ? "" : request.getContent())
                        .addValue("groupId", groupId)
                        .addValue("status", normalizeStatus(request.getStatus()))
                        .addValue("createTime", now)
                        .addValue("updateTime", now),
                keyHolder,
                new String[]{"id"});

        MemoResponse memo = getMemo(requiredKey(keyHolder));
        replaceRelatedUsers(memo.getId(), owner, relatedUserRequests(request.getRelatedUsers(), request.getRelatedUsernames()));
        memo = getMemo(memo.getId());

        // 创建 Memo 会影响列表和分组计数，所以同时广播 memo.created 和 group.updated。
        realtimePublisher.publishMemoChanged(owner, memoRecipients(memo.getId(), owner), "memo.created", memo.getId(), memo.getGroupId());
        realtimePublisher.publishGroupChanged(owner, "group.updated", memo.getGroupId());
        return memo;
    }

    @Override
    @Transactional
    public MemoResponse updateMemo(Long id, MemoUpdateRequest request) {
        String owner = currentUsername();
        boolean ownerCanManage = isMemoOwner(id, owner);
        requireMemoCanEdit(id, owner);
        Set<String> recipients = memoRecipients(id, owner);
        if (request.getGroupId() != null) {
            // 分组为空表示不调整分组；分组不为空时仍然必须校验归属。
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
                    update_time = :updateTime
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
                        .addValue("updateTime", LocalDateTime.now()));

        if (request.getRelatedUsers() != null || request.getRelatedUsernames() != null) {
            if (!ownerCanManage) {
                throw new BusinessException(ResultCode.PERMISSION_DENIED, "只有 Memo 创建人可以调整相关人");
            }
            replaceRelatedUsers(id, owner, relatedUserRequests(request.getRelatedUsers(), request.getRelatedUsernames()));
            recipients.addAll(memoRecipients(id, owner));
        }

        MemoResponse memo = getMemo(id);
        realtimePublisher.publishMemoChanged(owner, recipients, "memo.updated", memo.getId(), memo.getGroupId());
        return memo;
    }

    @Override
    @Transactional
    public MemoResponse updateRelatedUsers(Long id, MemoRelatedUsersUpdateRequest request) {
        String owner = currentUsername();
        requireMemoForOwner(id, owner);
        Set<String> recipients = memoRecipients(id, owner);
        replaceRelatedUsers(
                id,
                owner,
                request == null ? null : relatedUserRequests(request.getRelatedUsers(), request.getRelatedUsernames()));
        recipients.addAll(memoRecipients(id, owner));
        MemoResponse memo = getMemo(id);
        realtimePublisher.publishMemoChanged(owner, recipients, "memo.related.updated", memo.getId(), memo.getGroupId());
        return memo;
    }

    @Override
    @Transactional
    public void deleteMemo(Long id) {
        String owner = currentUsername();
        requireMemoForOwner(id, owner);
        Set<String> recipients = memoRecipients(id, owner);
        jdbcTemplate.update(
                """
                UPDATE uni_memo
                SET deleted = 1, deleted_time = :deletedTime, update_time = :updateTime
                WHERE id = :id AND owner_username = :owner AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("owner", owner)
                        .addValue("deletedTime", LocalDateTime.now())
                        .addValue("updateTime", LocalDateTime.now()));
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
        realtimePublisher.publishMemoChanged(owner, recipients, "memo.deleted", id, null);
        realtimePublisher.publishGroupChanged(owner, "group.updated", null);
    }

    @Override
    public MemoResponse updateTop(Long id, BooleanStateRequest request) {
        return updateMemoBoolean(id, "is_top", Boolean.TRUE.equals(request.getValue()));
    }

    @Override
    public MemoResponse updateFavorite(Long id, BooleanStateRequest request) {
        return updateMemoBoolean(id, "is_favorite", Boolean.TRUE.equals(request.getValue()));
    }

    @Override
    public MemoResponse updateArchive(Long id, BooleanStateRequest request) {
        return updateMemoBoolean(id, "is_archived", Boolean.TRUE.equals(request.getValue()));
    }

    @Override
    public List<MemoGroupResponse> listGroups() {
        String owner = currentUsername();
        ensureDefaultGroup(owner);
        return jdbcTemplate.query(
                """
                SELECT g.*,
                       (SELECT COUNT(*) FROM uni_memo m
                        WHERE m.owner_username = g.owner_username
                          AND m.group_id = g.id
                          AND m.deleted = 0) AS memo_count
                FROM uni_memo_group g
                WHERE g.owner_username = :owner AND g.deleted = 0
                ORDER BY g.sort_order ASC, g.id ASC
                """,
                Map.of("owner", owner),
                groupMapper());
    }

    @Override
    @Transactional
    public MemoGroupResponse createGroup(MemoGroupCreateRequest request) {
        String owner = currentUsername();
        ensureDefaultGroup(owner);
        LocalDateTime now = LocalDateTime.now();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                """
                INSERT INTO uni_memo_group
                    (owner_username, name, color, icon, sort_order, is_default, deleted, create_time, update_time)
                VALUES
                    (:owner, :name, :color, :icon, :sortOrder, 0, 0, :createTime, :updateTime)
                """,
                new MapSqlParameterSource()
                        .addValue("owner", owner)
                        .addValue("name", request.getName().trim())
                        .addValue("color", normalizeColor(request.getColor()))
                        .addValue("icon", normalizeIcon(request.getIcon()))
                        .addValue("sortOrder", nextSortOrder(owner))
                        .addValue("createTime", now)
                        .addValue("updateTime", now),
                keyHolder,
                new String[]{"id"});

        MemoGroupResponse group = requireGroupForOwner(requiredKey(keyHolder), owner);
        realtimePublisher.publishGroupChanged(owner, "group.created", group.getId());
        return group;
    }

    @Override
    @Transactional
    public MemoGroupResponse updateGroup(Long id, MemoGroupUpdateRequest request) {
        String owner = currentUsername();
        requireGroupForOwner(id, owner);
        jdbcTemplate.update(
                """
                UPDATE uni_memo_group
                SET name = :name,
                    color = :color,
                    icon = :icon,
                    sort_order = COALESCE(:sortOrder, sort_order),
                    update_time = :updateTime
                WHERE id = :id AND owner_username = :owner AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("owner", owner)
                        .addValue("name", request.getName().trim())
                        .addValue("color", normalizeColor(request.getColor()))
                        .addValue("icon", normalizeIcon(request.getIcon()))
                        .addValue("sortOrder", request.getSortOrder())
                        .addValue("updateTime", LocalDateTime.now()));

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

        jdbcTemplate.update(
                """
                UPDATE uni_memo_group
                SET deleted = 1, update_time = :updateTime
                WHERE id = :id AND owner_username = :owner AND deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("owner", owner)
                        .addValue("updateTime", LocalDateTime.now()));
        realtimePublisher.publishGroupChanged(owner, "group.deleted", id);
    }

    private MemoResponse updateMemoBoolean(Long id, String column, boolean value) {
        String owner = currentUsername();
        requireMemoForOwner(id, owner);

        // column 只由 updateTop/updateFavorite/updateArchive 三个内部方法传入，避免外部输入拼接 SQL。
        jdbcTemplate.update(
                "UPDATE uni_memo SET " + column + " = :value, update_time = :updateTime "
                        + "WHERE id = :id AND owner_username = :owner AND deleted = 0",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("owner", owner)
                        .addValue("value", value ? 1 : 0)
                        .addValue("updateTime", LocalDateTime.now()));
        MemoResponse memo = getMemo(id);
        realtimePublisher.publishMemoChanged(owner, memoRecipients(id, owner), "memo.updated", memo.getId(), memo.getGroupId());
        return memo;
    }

    private MemoResponse findMemoResponse(Long id, String owner) {
        List<MemoResponse> list = jdbcTemplate.query(
                """
                SELECT m.*, g.name AS group_name
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

        List<MemoGroupResponse> list = jdbcTemplate.query(
                """
                SELECT g.*,
                       (SELECT COUNT(*) FROM uni_memo m
                        WHERE m.owner_username = g.owner_username
                          AND m.group_id = g.id
                          AND m.deleted = 0) AS memo_count
                FROM uni_memo_group g
                WHERE g.id = :id AND g.owner_username = :owner AND g.deleted = 0
                """,
                new MapSqlParameterSource()
                        .addValue("id", groupId)
                        .addValue("owner", owner),
                groupMapper());

        if (list.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分组不存在");
        }
        return list.getFirst();
    }

    private MemoGroupResponse ensureDefaultGroup(String owner) {
        // 默认分组是用户首次使用 Memo 的基础数据。这里采用惰性创建，避免额外初始化流程。
        List<MemoGroupResponse> exists = jdbcTemplate.query(
                """
                SELECT g.*,
                       (SELECT COUNT(*) FROM uni_memo m
                        WHERE m.owner_username = g.owner_username
                          AND m.group_id = g.id
                          AND m.deleted = 0) AS memo_count
                FROM uni_memo_group g
                WHERE g.owner_username = :owner AND g.is_default = 1 AND g.deleted = 0
                ORDER BY g.id ASC
                LIMIT 1
                """,
                Map.of("owner", owner),
                groupMapper());

        if (!exists.isEmpty()) {
            return exists.getFirst();
        }

        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                """
                INSERT INTO uni_memo_group
                    (owner_username, name, color, icon, sort_order, is_default, deleted, create_time, update_time)
                VALUES
                    (:owner, :name, :color, :icon, 0, 1, 0, :createTime, :updateTime)
                """,
                new MapSqlParameterSource()
                        .addValue("owner", owner)
                        .addValue("name", DEFAULT_GROUP_NAME)
                        .addValue("color", DEFAULT_GROUP_COLOR)
                        .addValue("icon", DEFAULT_GROUP_ICON)
                        .addValue("createTime", now)
                        .addValue("updateTime", now),
                keyHolder,
                new String[]{"id"});

        return requireGroupForOwner(requiredKey(keyHolder), owner);
    }

    private int nextSortOrder(String owner) {
        // sort_order 按 10 递增，后续如果支持拖拽排序，可以在两个相邻值中插入新值。
        Integer maxSort = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(MAX(sort_order), 0)
                FROM uni_memo_group
                WHERE owner_username = :owner AND deleted = 0
                """,
                Map.of("owner", owner),
                Integer.class);
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

    private Set<String> memoRecipients(Long memoId, String owner) {
        Set<String> recipients = new LinkedHashSet<>();
        recipients.add(owner);
        recipients.addAll(relatedUsernames(memoId));
        return recipients;
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
        return memo;
    }

    private List<MemoResponse> enrichMemoResponses(List<MemoResponse> memos, String currentUsername) {
        List<MemoResponse> enriched = new ArrayList<>(memos.size());
        for (MemoResponse memo : memos) {
            enriched.add(enrichMemoResponse(memo, currentUsername));
        }
        return enriched;
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
        if (StpUtil.isLogin()) {
            return StpUtil.getLoginIdAsString();
        }

        // 临时联调用：正式开启 Memo 接口鉴权后，未登录请求应在拦截器层被拒绝。
        return DEV_FALLBACK_USERNAME;
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
                .isFavorite(rs.getBoolean("is_favorite"))
                .isArchived(rs.getBoolean("is_archived"))
                .createTime(toLocalDateTime(rs, "create_time"))
                .updateTime(toLocalDateTime(rs, "update_time"))
                .build();
    }

    private RowMapper<MemoGroupResponse> groupMapper() {
        // memo_count 来自查询里的子查询别名，调用方无需再次查询分组下 Memo 数量。
        return (rs, rowNum) -> MemoGroupResponse.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .color(rs.getString("color"))
                .icon(rs.getString("icon"))
                .sortOrder(rs.getInt("sort_order"))
                .isDefault(rs.getBoolean("is_default"))
                .memoCount(rs.getLong("memo_count"))
                .createTime(toLocalDateTime(rs, "create_time"))
                .updateTime(toLocalDateTime(rs, "update_time"))
                .build();
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
