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
import com.unicomm.module.memo.entity.MemoEntity;
import com.unicomm.module.memo.entity.MemoRelatedUserEntity;
import com.unicomm.module.memo.mapper.MemoFavoriteMapper;
import com.unicomm.module.memo.mapper.MemoGroupMapper;
import com.unicomm.module.memo.mapper.MemoMapper;
import com.unicomm.module.memo.mapper.MemoRelatedUserMapper;
import com.unicomm.module.memo.mapper.MemoTopMapper;
import com.unicomm.module.memo.realtime.MemoRealtimePublisher;
import com.unicomm.module.member.dto.MemberSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MyBatisMemoService implements MemoService {

    /*
     * 当前 Memo 模块采用 MyBatis-Plus Mapper 作为持久化边界。
     *
     * 设计约束：
     * 1. Memo 仍有明确 owner_username；列表和详情同时允许 owner 与相关人查看。
     * 2. 删除采用逻辑删除，避免误删后无法恢复，也便于后续做回收站。
     * 3. 写操作完成后发布 WebSocket 事件，桌面端没有刷新按钮，依赖事件触发重新拉取。
     * 4. 简单 CRUD 和复杂显式 SQL 都收敛到 Mapper；Service 只保留业务编排、权限入口和通知发布。
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

    private final MemoFavoriteMapper memoFavoriteMapper;
    private final MemoGroupMapper memoGroupMapper;
    private final MemoMapper memoMapper;
    private final MemoRelatedUserMapper memoRelatedUserMapper;
    private final MemoTopMapper memoTopMapper;
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

        String keywordPattern = StringUtils.hasText(keyword) ? "%" + keyword.trim().toLowerCase() + "%" : null;

        long safeTotal = memoMapper.countVisible(owner, groupId, keywordPattern, isShared, isFavorite, status);
        // 排序策略由 Mapper 固定：个人置顶优先，其次按最后更新时间倒序，最后用 id 倒序保证相同时间下列表稳定。
        List<MemoResponse> list = memoMapper.selectVisiblePage(
                owner,
                groupId,
                keywordPattern,
                isShared,
                isFavorite,
                status,
                safeSize,
                offset);

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

        MemoEntity entity = new MemoEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setOwnerUsername(owner);
        entity.setTitle(normalizeTitle(request.getTitle()));
        entity.setContent(request.getContent() == null ? "" : request.getContent());
        entity.setGroupId(groupId);
        entity.setStatus(normalizeStatus(request.getStatus()));
        entity.setDeleted(false);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setUpdateUsername(owner);
        memoMapper.insert(entity);

        MemoResponse memo = getMemo(entity.getId());
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

        memoMapper.updateMemoFields(
                id,
                ownerCanManage ? 1 : 0,
                normalizeTitle(request.getTitle()),
                request.getContent() == null ? "" : request.getContent(),
                request.getGroupId(),
                normalizeStatus(request.getStatus()),
                LocalDateTime.now(),
                owner);

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
        LocalDateTime deletedTime = LocalDateTime.now();
        memoMapper.softDeleteByOwner(id, owner, deletedTime, deletedTime, owner);
        memoRelatedUserMapper.softDeleteByMemoOwner(id, owner, LocalDateTime.now());
        LocalDateTime stateDeletedTime = LocalDateTime.now();
        memoFavoriteMapper.softDeleteByMemoOwner(id, owner, stateDeletedTime);
        memoTopMapper.softDeleteByMemoOwner(id, owner, stateDeletedTime);
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
        memoTopMapper.upsertState(id, username, memo.getOwnerUsername(), !value, now);
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
        memoFavoriteMapper.upsertState(id, username, memo.getOwnerUsername(), !value, now);
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
        memoMapper.moveGroupMemosToDefault(owner, group.getId(), defaultGroup.getId(), LocalDateTime.now());

        memoGroupMapper.softDeleteGroup(id, owner, LocalDateTime.now());
        realtimePublisher.publishGroupChanged(owner, "group.deleted", id);
    }

    private MemoResponse findMemoResponse(Long id, String owner) {
        return enrichMemoResponse(memoMapper.selectVisibleById(id, owner), owner);
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
        return memoMapper.countActiveByOwner(id, owner) > 0;
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
        return memoMapper.selectPermission(id, username, PERMISSION_OWNER);
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
        memoMapper.moveDuplicateDefaultGroupMemos(owner, duplicateIds, defaultGroup.getId(), now);

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
        memoRelatedUserMapper.softDeleteByMemoOwner(memoId, owner, now);

        for (Map.Entry<String, String> relatedUser : normalized.entrySet()) {
            memoRelatedUserMapper.upsertRelatedUser(memoId, owner, relatedUser.getKey(), relatedUser.getValue(), now);
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
        return memoMapper.selectOwnerUsername(memoId);
    }

    private List<String> relatedUsernames(Long memoId) {
        return memoRelatedUserMapper.selectActiveUsernamesByMemoId(memoId);
    }

    private boolean isFavorite(Long memoId, String username) {
        return memoFavoriteMapper.countActiveByMemoAndUser(memoId, username) > 0;
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
        memoMapper.touch(id, updateUsername, LocalDateTime.now());
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
        return memoRelatedUserMapper.selectActiveByMemoId(memoId).stream()
                .map(this::toRelatedUserResponse)
                .toList();
    }

    private MemoRelatedUserResponse toRelatedUserResponse(MemoRelatedUserEntity relatedUser) {
        String username = relatedUser.getRelatedUsername();
        MemberSearchResponse member = findMember(username);
        return MemoRelatedUserResponse.builder()
                .id(relatedUser.getId())
                .username(username)
                .employeeNo(member == null ? null : member.getEmployeeNo())
                .displayName(member == null ? null : member.getDisplayName())
                .departmentName(member == null ? null : member.getDepartmentName())
                .email(member == null ? null : member.getEmail())
                .permission(relatedUser.getPermission())
                .createTime(relatedUser.getCreateTime())
                .updateTime(relatedUser.getUpdateTime())
                .build();
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

}
