package com.unicomm.module.memo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Memo 主表实体。
 *
 * <p>该实体只表达数据库字段。列表展示、当前用户权限、收藏/置顶状态等视图字段
 * 继续由 Mapper 查询到响应 DTO，避免把用户视角状态混入主表模型。</p>
 */
@Data
@TableName("uni_memo")
public class MemoEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ownerUsername;
    private String title;
    private String content;
    private Long groupId;
    private String status;
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String updateUsername;
    private LocalDateTime deletedTime;
}
