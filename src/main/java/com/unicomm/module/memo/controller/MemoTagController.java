package com.unicomm.module.memo.controller;

import com.unicomm.common.Result;
import com.unicomm.module.memo.dto.MemoDtos.MemoTagCreateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoTagResponse;
import com.unicomm.module.memo.dto.MemoDtos.MemoTagUpdateRequest;
import com.unicomm.module.memo.service.MemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/memo-tags")
@RequiredArgsConstructor
@Tag(name = "备忘录标签模块", description = "个人备忘录标签管理接口")
public class MemoTagController {

    private final MemoService memoService;

    @GetMapping
    @Operation(summary = "获取标签列表")
    public Result<List<MemoTagResponse>> list() {
        return Result.success(memoService.listTags());
    }

    @PostMapping
    @Operation(summary = "创建标签")
    public Result<MemoTagResponse> create(@Valid @RequestBody MemoTagCreateRequest request) {
        return Result.success(memoService.createTag(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新标签")
    public Result<MemoTagResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody MemoTagUpdateRequest request) {

        return Result.success(memoService.updateTag(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除标签")
    public Result<Void> delete(@PathVariable Long id) {
        memoService.deleteTag(id);
        return Result.success("删除成功", null);
    }
}
