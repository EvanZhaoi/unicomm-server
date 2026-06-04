package com.unicomm.module.memo.controller;

import com.unicomm.common.PageResult;
import com.unicomm.common.Result;
import com.unicomm.module.memo.dto.MemoDtos.BooleanStateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoCreateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoRelatedUsersUpdateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoResponse;
import com.unicomm.module.memo.dto.MemoDtos.MemoUpdateRequest;
import com.unicomm.module.memo.service.MemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memos")
@RequiredArgsConstructor
@Tag(name = "备忘录模块", description = "个人备忘录管理接口")
public class MemoController {

    private final MemoService memoService;

    @GetMapping
    @Operation(summary = "分页查询 Memo")
    public Result<PageResult<MemoResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Boolean isFavorite,
            @RequestParam(required = false) String status) {

        return Result.success(memoService.listMemos(page, size, groupId, keyword, tagId, isFavorite, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取 Memo 详情")
    public Result<MemoResponse> detail(@PathVariable Long id) {
        return Result.success(memoService.getMemo(id));
    }

    @PostMapping
    @Operation(summary = "创建 Memo")
    public Result<MemoResponse> create(@Valid @RequestBody MemoCreateRequest request) {
        return Result.success(memoService.createMemo(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 Memo")
    public Result<MemoResponse> update(@PathVariable Long id, @Valid @RequestBody MemoUpdateRequest request) {
        return Result.success(memoService.updateMemo(id, request));
    }

    @PutMapping("/{id}/related-users")
    @Operation(summary = "更新 Memo 相关人")
    public Result<MemoResponse> updateRelatedUsers(
            @PathVariable Long id,
            @RequestBody MemoRelatedUsersUpdateRequest request) {
        return Result.success(memoService.updateRelatedUsers(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 Memo")
    public Result<Void> delete(@PathVariable Long id) {
        memoService.deleteMemo(id);
        return Result.success("删除成功", null);
    }

    @PatchMapping("/{id}/top")
    @Operation(summary = "置顶或取消置顶")
    public Result<MemoResponse> top(@PathVariable Long id, @RequestBody BooleanStateRequest request) {
        return Result.success(memoService.updateTop(id, request));
    }

    @PatchMapping("/{id}/favorite")
    @Operation(summary = "收藏或取消收藏")
    public Result<MemoResponse> favorite(@PathVariable Long id, @RequestBody BooleanStateRequest request) {
        return Result.success(memoService.updateFavorite(id, request));
    }

}
