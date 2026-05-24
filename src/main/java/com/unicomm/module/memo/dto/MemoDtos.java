package com.unicomm.module.memo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class MemoDtos {

    private MemoDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Memo 响应")
    public static class MemoResponse {
        private Long id;
        private String title;
        private String content;
        private Long groupId;
        private String groupName;
        private String status;
        private Boolean isTop;
        private Boolean isFavorite;
        private Boolean isArchived;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "创建 Memo 请求")
    public static class MemoCreateRequest {
        @Size(max = 200, message = "标题最多 200 字符")
        private String title;

        private String content;

        private Long groupId;

        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "更新 Memo 请求")
    public static class MemoUpdateRequest {
        @Size(max = 200, message = "标题最多 200 字符")
        private String title;

        private String content;

        private Long groupId;

        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "布尔状态更新请求")
    public static class BooleanStateRequest {
        private Boolean value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Memo 分组响应")
    public static class MemoGroupResponse {
        private Long id;
        private String name;
        private String color;
        private String icon;
        private Integer sortOrder;
        private Boolean isDefault;
        private Long memoCount;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "创建 Memo 分组请求")
    public static class MemoGroupCreateRequest {
        @NotBlank(message = "分组名称不能为空")
        @Size(max = 50, message = "分组名称最多 50 字符")
        private String name;

        private String color;

        private String icon;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "更新 Memo 分组请求")
    public static class MemoGroupUpdateRequest {
        @NotBlank(message = "分组名称不能为空")
        @Size(max = 50, message = "分组名称最多 50 字符")
        private String name;

        private String color;

        private String icon;

        private Integer sortOrder;
    }
}
