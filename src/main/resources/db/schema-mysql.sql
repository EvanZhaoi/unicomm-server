CREATE DATABASE IF NOT EXISTS unicomm
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE unicomm;

CREATE TABLE IF NOT EXISTS uni_memo_group (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    owner_username VARCHAR(100) NOT NULL COMMENT '所有者用户名，用于用户数据隔离',
    name VARCHAR(50) NOT NULL COMMENT '分组名称',
    color VARCHAR(32) NOT NULL DEFAULT '#6B7280' COMMENT '分组颜色，HEX 或主题色标识',
    icon VARCHAR(64) NOT NULL DEFAULT 'folder' COMMENT '分组图标名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认分组：0=否，1=是',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_memo_group_owner (owner_username, deleted, sort_order),
    KEY idx_memo_group_default (owner_username, is_default, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Memo 分组表';

CREATE TABLE IF NOT EXISTS uni_memo (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    owner_username VARCHAR(100) NOT NULL COMMENT '所有者用户名，用于用户数据隔离',
    title VARCHAR(200) NOT NULL COMMENT 'Memo 标题',
    content TEXT NULL COMMENT 'Memo 正文内容',
    group_id BIGINT NOT NULL COMMENT '所属分组ID',
    status VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT '状态：normal=普通，todo=待办，done=完成',
    is_top TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶：0=否，1=是',
    is_favorite TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收藏：0=否，1=是',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    deleted_time DATETIME NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    KEY idx_memo_owner_list (owner_username, deleted, is_top, update_time),
    KEY idx_memo_owner_group (owner_username, group_id, deleted),
    KEY idx_memo_owner_favorite (owner_username, is_favorite, deleted),
    CONSTRAINT fk_uni_memo_group
        FOREIGN KEY (group_id) REFERENCES uni_memo_group (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Memo 主表';

CREATE TABLE IF NOT EXISTS uni_memo_related_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    memo_id BIGINT NOT NULL COMMENT '关联的 Memo ID',
    owner_username VARCHAR(100) NOT NULL COMMENT 'Memo 所有者用户名，便于所有者维度查询和权限校验',
    related_username VARCHAR(100) NOT NULL COMMENT '相关人用户名，相关人可以查看该 Memo',
    permission VARCHAR(20) NOT NULL DEFAULT 'view' COMMENT '相关人权限：view=仅查看，edit=可编辑标题、正文和状态',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memo_related_user (memo_id, related_username),
    KEY idx_memo_related_visible (related_username, deleted, memo_id),
    KEY idx_memo_related_owner (owner_username, deleted, memo_id),
    CONSTRAINT fk_uni_memo_related_memo
        FOREIGN KEY (memo_id) REFERENCES uni_memo (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Memo 相关人表';

CREATE TABLE IF NOT EXISTS uni_memo_tag (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    owner_username VARCHAR(100) NOT NULL COMMENT '标签所有者用户名，用于用户数据隔离',
    name VARCHAR(20) NOT NULL COMMENT '标签名称',
    color VARCHAR(32) NOT NULL DEFAULT '#6B7280' COMMENT '标签颜色，HEX 或主题色标识',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memo_tag_owner_name (owner_username, name, deleted),
    KEY idx_memo_tag_owner (owner_username, deleted, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Memo 标签表';

CREATE TABLE IF NOT EXISTS uni_memo_tag_rel (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    memo_id BIGINT NOT NULL COMMENT '关联的 Memo ID',
    tag_id BIGINT NOT NULL COMMENT '关联的标签 ID',
    owner_username VARCHAR(100) NOT NULL COMMENT 'Memo 所有者用户名，便于权限校验和清理',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memo_tag_rel (memo_id, tag_id),
    KEY idx_memo_tag_rel_tag (tag_id, deleted, memo_id),
    KEY idx_memo_tag_rel_memo (memo_id, deleted),
    CONSTRAINT fk_uni_memo_tag_rel_memo
        FOREIGN KEY (memo_id) REFERENCES uni_memo (id),
    CONSTRAINT fk_uni_memo_tag_rel_tag
        FOREIGN KEY (tag_id) REFERENCES uni_memo_tag (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Memo 标签关联表';
