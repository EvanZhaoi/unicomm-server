package com.unicomm.module.memo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Memo 用户置顶实体。
 *
 * <p>置顶只改变当前用户自己的 Memo 排序。该状态不能写回 Memo 主表，
 * 否则共享 Memo 会在所有相关人的列表中一起置顶。</p>
 */
@Data
@TableName("uni_memo_top")
public class MemoTopEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memoId;
    private String username;
    private String ownerUsername;
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
