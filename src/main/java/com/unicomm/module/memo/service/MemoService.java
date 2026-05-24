package com.unicomm.module.memo.service;

import cn.dev33.satoken.stp.StpUtil;
import com.unicomm.common.BusinessException;
import com.unicomm.common.PageResult;
import com.unicomm.common.ResultCode;
import com.unicomm.module.memo.dto.MemoDtos.BooleanStateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoCreateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupCreateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupResponse;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupUpdateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoResponse;
import com.unicomm.module.memo.dto.MemoDtos.MemoUpdateRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class MemoService {

    private static final String DEFAULT_GROUP_NAME = "我的备忘";
    private static final String DEFAULT_GROUP_COLOR = "#6B7280";
    private static final String DEFAULT_GROUP_ICON = "folder";
    private static final String DEFAULT_STATUS = "normal";
    private static final String DEV_FALLBACK_USERNAME = "evan.zhao";

    private final AtomicLong memoIdGenerator = new AtomicLong(1);
    private final AtomicLong groupIdGenerator = new AtomicLong(1);
    private final Map<Long, MemoRecord> memos = new ConcurrentHashMap<>();
    private final Map<Long, MemoGroupRecord> groups = new ConcurrentHashMap<>();

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

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);

        List<MemoResponse> filtered = memos.values().stream()
                .filter(memo -> owner.equals(memo.getOwnerUsername()))
                .filter(memo -> !memo.isDeleted())
                .filter(memo -> groupId == null || Objects.equals(memo.getGroupId(), groupId))
                .filter(memo -> isArchived == null || memo.isArchived() == isArchived)
                .filter(memo -> isFavorite == null || memo.isFavorite() == isFavorite)
                .filter(memo -> !StringUtils.hasText(status) || status.equals(memo.getStatus()))
                .filter(memo -> matchesKeyword(memo, keyword))
                .sorted(Comparator
                        .comparing(MemoRecord::isTop).reversed()
                        .thenComparing(MemoRecord::getUpdateTime).reversed())
                .map(this::toMemoResponse)
                .toList();

        int fromIndex = Math.min((safePage - 1) * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        long total = filtered.size();
        long pages = total == 0 ? 0 : (total + safeSize - 1) / safeSize;

        return PageResult.<MemoResponse>builder()
                .list(filtered.subList(fromIndex, toIndex))
                .total(total)
                .page(safePage)
                .size(safeSize)
                .pages(pages)
                .build();
    }

    public MemoResponse getMemo(Long id) {
        return toMemoResponse(requireMemo(id));
    }

    public MemoResponse createMemo(MemoCreateRequest request) {
        String owner = currentUsername();
        MemoGroupRecord defaultGroup = ensureDefaultGroup(owner);

        Long groupId = request.getGroupId() == null ? defaultGroup.getId() : request.getGroupId();
        requireGroupForOwner(groupId, owner);

        LocalDateTime now = LocalDateTime.now();
        MemoRecord memo = new MemoRecord();
        memo.setId(memoIdGenerator.getAndIncrement());
        memo.setOwnerUsername(owner);
        memo.setTitle(normalizeTitle(request.getTitle()));
        memo.setContent(request.getContent() == null ? "" : request.getContent());
        memo.setGroupId(groupId);
        memo.setStatus(normalizeStatus(request.getStatus()));
        memo.setTop(false);
        memo.setFavorite(false);
        memo.setArchived(false);
        memo.setDeleted(false);
        memo.setCreateTime(now);
        memo.setUpdateTime(now);

        memos.put(memo.getId(), memo);
        return toMemoResponse(memo);
    }

    public MemoResponse updateMemo(Long id, MemoUpdateRequest request) {
        MemoRecord memo = requireMemo(id);

        if (request.getGroupId() != null) {
            requireGroupForOwner(request.getGroupId(), memo.getOwnerUsername());
            memo.setGroupId(request.getGroupId());
        }
        memo.setTitle(normalizeTitle(request.getTitle()));
        memo.setContent(request.getContent() == null ? "" : request.getContent());
        memo.setStatus(normalizeStatus(request.getStatus()));
        memo.setUpdateTime(LocalDateTime.now());

        return toMemoResponse(memo);
    }

    public void deleteMemo(Long id) {
        MemoRecord memo = requireMemo(id);
        memo.setDeleted(true);
        memo.setDeletedTime(LocalDateTime.now());
        memo.setUpdateTime(LocalDateTime.now());
    }

    public MemoResponse updateTop(Long id, BooleanStateRequest request) {
        MemoRecord memo = requireMemo(id);
        memo.setTop(Boolean.TRUE.equals(request.getValue()));
        memo.setUpdateTime(LocalDateTime.now());
        return toMemoResponse(memo);
    }

    public MemoResponse updateFavorite(Long id, BooleanStateRequest request) {
        MemoRecord memo = requireMemo(id);
        memo.setFavorite(Boolean.TRUE.equals(request.getValue()));
        memo.setUpdateTime(LocalDateTime.now());
        return toMemoResponse(memo);
    }

    public MemoResponse updateArchive(Long id, BooleanStateRequest request) {
        MemoRecord memo = requireMemo(id);
        memo.setArchived(Boolean.TRUE.equals(request.getValue()));
        memo.setUpdateTime(LocalDateTime.now());
        return toMemoResponse(memo);
    }

    public List<MemoGroupResponse> listGroups() {
        String owner = currentUsername();
        ensureDefaultGroup(owner);

        return groups.values().stream()
                .filter(group -> owner.equals(group.getOwnerUsername()))
                .filter(group -> !group.isDeleted())
                .sorted(Comparator.comparing(MemoGroupRecord::getSortOrder).thenComparing(MemoGroupRecord::getId))
                .map(this::toGroupResponse)
                .toList();
    }

    public MemoGroupResponse createGroup(MemoGroupCreateRequest request) {
        String owner = currentUsername();
        ensureDefaultGroup(owner);

        LocalDateTime now = LocalDateTime.now();
        MemoGroupRecord group = new MemoGroupRecord();
        group.setId(groupIdGenerator.getAndIncrement());
        group.setOwnerUsername(owner);
        group.setName(request.getName().trim());
        group.setColor(normalizeColor(request.getColor()));
        group.setIcon(normalizeIcon(request.getIcon()));
        group.setSortOrder(nextSortOrder(owner));
        group.setDefault(false);
        group.setDeleted(false);
        group.setCreateTime(now);
        group.setUpdateTime(now);

        groups.put(group.getId(), group);
        return toGroupResponse(group);
    }

    public MemoGroupResponse updateGroup(Long id, MemoGroupUpdateRequest request) {
        MemoGroupRecord group = requireGroup(id);
        group.setName(request.getName().trim());
        group.setColor(normalizeColor(request.getColor()));
        group.setIcon(normalizeIcon(request.getIcon()));
        if (request.getSortOrder() != null) {
            group.setSortOrder(request.getSortOrder());
        }
        group.setUpdateTime(LocalDateTime.now());
        return toGroupResponse(group);
    }

    public void deleteGroup(Long id) {
        MemoGroupRecord group = requireGroup(id);
        if (group.isDefault()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "默认分组不可删除");
        }

        MemoGroupRecord defaultGroup = ensureDefaultGroup(group.getOwnerUsername());
        memos.values().stream()
                .filter(memo -> group.getOwnerUsername().equals(memo.getOwnerUsername()))
                .filter(memo -> Objects.equals(memo.getGroupId(), group.getId()))
                .forEach(memo -> {
                    memo.setGroupId(defaultGroup.getId());
                    memo.setUpdateTime(LocalDateTime.now());
                });

        group.setDeleted(true);
        group.setUpdateTime(LocalDateTime.now());
    }

    private MemoRecord requireMemo(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Memo ID 不能为空");
        }
        MemoRecord memo = memos.get(id);
        if (memo == null || memo.isDeleted() || !currentUsername().equals(memo.getOwnerUsername())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Memo 不存在");
        }
        return memo;
    }

    private MemoGroupRecord requireGroup(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分组 ID 不能为空");
        }
        MemoGroupRecord group = groups.get(id);
        if (group == null || group.isDeleted() || !currentUsername().equals(group.getOwnerUsername())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分组不存在");
        }
        return group;
    }

    private MemoGroupRecord requireGroupForOwner(Long groupId, String owner) {
        MemoGroupRecord group = groups.get(groupId);
        if (group == null || group.isDeleted() || !owner.equals(group.getOwnerUsername())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分组不存在");
        }
        return group;
    }

    private MemoGroupRecord ensureDefaultGroup(String owner) {
        return groups.values().stream()
                .filter(group -> owner.equals(group.getOwnerUsername()))
                .filter(MemoGroupRecord::isDefault)
                .filter(group -> !group.isDeleted())
                .findFirst()
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    MemoGroupRecord group = new MemoGroupRecord();
                    group.setId(groupIdGenerator.getAndIncrement());
                    group.setOwnerUsername(owner);
                    group.setName(DEFAULT_GROUP_NAME);
                    group.setColor(DEFAULT_GROUP_COLOR);
                    group.setIcon(DEFAULT_GROUP_ICON);
                    group.setSortOrder(0);
                    group.setDefault(true);
                    group.setDeleted(false);
                    group.setCreateTime(now);
                    group.setUpdateTime(now);
                    groups.put(group.getId(), group);
                    log.info("创建默认 Memo 分组: owner={}, groupId={}", owner, group.getId());
                    return group;
                });
    }

    private int nextSortOrder(String owner) {
        return groups.values().stream()
                .filter(group -> owner.equals(group.getOwnerUsername()))
                .filter(group -> !group.isDeleted())
                .mapToInt(MemoGroupRecord::getSortOrder)
                .max()
                .orElse(0) + 10;
    }

    private boolean matchesKeyword(MemoRecord memo, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return memo.getTitle().toLowerCase().contains(normalized)
                || memo.getContent().toLowerCase().contains(normalized);
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return "无标题";
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
        return DEV_FALLBACK_USERNAME;
    }

    private MemoResponse toMemoResponse(MemoRecord memo) {
        MemoGroupRecord group = groups.get(memo.getGroupId());
        return MemoResponse.builder()
                .id(memo.getId())
                .title(memo.getTitle())
                .content(memo.getContent())
                .groupId(memo.getGroupId())
                .groupName(group == null ? "" : group.getName())
                .status(memo.getStatus())
                .isTop(memo.isTop())
                .isFavorite(memo.isFavorite())
                .isArchived(memo.isArchived())
                .createTime(memo.getCreateTime())
                .updateTime(memo.getUpdateTime())
                .build();
    }

    private MemoGroupResponse toGroupResponse(MemoGroupRecord group) {
        long memoCount = memos.values().stream()
                .filter(memo -> group.getOwnerUsername().equals(memo.getOwnerUsername()))
                .filter(memo -> Objects.equals(memo.getGroupId(), group.getId()))
                .filter(memo -> !memo.isDeleted())
                .count();

        return MemoGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .color(group.getColor())
                .icon(group.getIcon())
                .sortOrder(group.getSortOrder())
                .isDefault(group.isDefault())
                .memoCount(memoCount)
                .createTime(group.getCreateTime())
                .updateTime(group.getUpdateTime())
                .build();
    }

    @Data
    private static class MemoRecord {
        private Long id;
        private String title;
        private String content;
        private Long groupId;
        private String status;
        private boolean top;
        private boolean favorite;
        private boolean archived;
        private String ownerUsername;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private boolean deleted;
        private LocalDateTime deletedTime;
    }

    @Data
    private static class MemoGroupRecord {
        private Long id;
        private String name;
        private String color;
        private String icon;
        private Integer sortOrder;
        private String ownerUsername;
        private boolean isDefault;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private boolean deleted;
    }
}
