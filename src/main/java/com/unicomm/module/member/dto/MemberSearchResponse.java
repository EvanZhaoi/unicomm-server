package com.unicomm.module.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "成员搜索响应")
public class MemberSearchResponse {

    private String username;

    private String employeeNo;

    private String displayName;

    private String departmentName;

    private String email;
}
