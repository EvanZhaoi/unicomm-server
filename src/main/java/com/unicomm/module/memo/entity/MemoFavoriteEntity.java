package com.unicomm.module.memo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Memo 用户收藏实体。
 *
 * <p>收藏是“用户 + Memo”的个人状态。多人共享同一个 Memo 时，
 * 某个用户收藏或取消收藏不能影响创建人和其他相关人的列表。</p>
 */
@Data
@TableName("uni_memo_favorite")
public class MemoFavoriteEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memoId;
    private String username;
    private String ownerUsername;
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
