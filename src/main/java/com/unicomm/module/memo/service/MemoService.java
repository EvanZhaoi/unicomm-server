package com.unicomm.module.memo.service;

import com.unicomm.common.PageResult;
import com.unicomm.module.memo.dto.MemoDtos.BooleanStateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoCreateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupCreateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupResponse;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupUpdateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoRelatedUsersUpdateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoResponse;
import com.unicomm.module.memo.dto.MemoDtos.MemoTagCreateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoTagResponse;
import com.unicomm.module.memo.dto.MemoDtos.MemoTagUpdateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoUpdateRequest;

import java.util.List;

public interface MemoService {

    PageResult<MemoResponse> listMemos(
            Integer page,
            Integer size,
            Long groupId,
            String keyword,
            Long tagId,
            Boolean isFavorite,
            String status);

    MemoResponse getMemo(Long id);

    MemoResponse createMemo(MemoCreateRequest request);

    MemoResponse updateMemo(Long id, MemoUpdateRequest request);

    MemoResponse updateRelatedUsers(Long id, MemoRelatedUsersUpdateRequest request);

    void deleteMemo(Long id);

    MemoResponse updateTop(Long id, BooleanStateRequest request);

    MemoResponse updateFavorite(Long id, BooleanStateRequest request);

    List<MemoGroupResponse> listGroups();

    MemoGroupResponse createGroup(MemoGroupCreateRequest request);

    MemoGroupResponse updateGroup(Long id, MemoGroupUpdateRequest request);

    void deleteGroup(Long id);

    List<MemoTagResponse> listTags();

    MemoTagResponse createTag(MemoTagCreateRequest request);

    MemoTagResponse updateTag(Long id, MemoTagUpdateRequest request);

    void deleteTag(Long id);
}
