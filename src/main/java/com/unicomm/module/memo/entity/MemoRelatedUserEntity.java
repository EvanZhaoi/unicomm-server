package com.unicomm.module.memo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Memo 相关人实体。
 *
 * <p>相关人决定共享 Memo 的可见范围和协作权限。权限只允许 `view` 和 `edit`，
 * 具体合法性由服务层校验，Mapper 只负责关系表的幂等写入和查询。</p>
 */
@Data
@TableName("uni_memo_related_user")
public class MemoRelatedUserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memoId;
    private String ownerUsername;
    private String relatedUsername;
    private String permission;
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
