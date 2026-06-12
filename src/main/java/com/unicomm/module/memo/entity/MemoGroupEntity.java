package com.unicomm.module.memo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Memo 分组实体。
 *
 * <p>分组属于当前登录用户，是 Memo 左侧导航和编辑页分组选择的数据来源。
 * 该实体先承接简单 CRUD，带 Memo 数量的聚合查询仍放在 Mapper 的显式 SQL 中。</p>
 */
@Data
@TableName("uni_memo_group")
public class MemoGroupEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ownerUsername;
    private String name;
    private String color;
    private String icon;
    private Integer sortOrder;
    @TableField("is_default")
    private Boolean isDefault;
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
