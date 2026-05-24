package com.unicomm.module.memo.controller;

import com.unicomm.common.Result;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupCreateRequest;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupResponse;
import com.unicomm.module.memo.dto.MemoDtos.MemoGroupUpdateRequest;
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
@RequestMapping("/api/v1/memo-groups")
@RequiredArgsConstructor
@Tag(name = "备忘录分组模块", description = "个人备忘录分组管理接口")
public class MemoGroupController {

    private final MemoService memoService;

    @GetMapping
    @Operation(summary = "获取分组列表")
    public Result<List<MemoGroupResponse>> list() {
        return Result.success(memoService.listGroups());
    }

    @PostMapping
    @Operation(summary = "创建分组")
    public Result<MemoGroupResponse> create(@Valid @RequestBody MemoGroupCreateRequest request) {
        return Result.success(memoService.createGroup(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新分组")
    public Result<MemoGroupResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody MemoGroupUpdateRequest request) {

        return Result.success(memoService.updateGroup(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分组")
    public Result<Void> delete(@PathVariable Long id) {
        memoService.deleteGroup(id);
        return Result.success("删除成功", null);
    }
}
