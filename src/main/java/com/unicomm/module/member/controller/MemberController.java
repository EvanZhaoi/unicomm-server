package com.unicomm.module.member.controller;

import com.unicomm.common.Result;
import com.unicomm.module.auth.service.AuthService;
import com.unicomm.module.member.dto.MemberSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "成员模块", description = "员工成员搜索接口")
public class MemberController {

    private final AuthService authService;

    @GetMapping("/search")
    @Operation(summary = "按姓名、用户名、工号或邮箱模糊搜索成员")
    public Result<List<MemberSearchResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        return Result.success(authService.searchMembers(keyword, limit));
    }
}
