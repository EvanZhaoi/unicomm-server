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
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    update_username VARCHAR(100) NOT NULL DEFAULT '' COMMENT '最后更新人用户名',
    deleted_time DATETIME NULL COMMENT '删除时间',
    PRIMARY KEY (id),
    KEY idx_memo_owner_list (owner_username, deleted, update_time, id),
    KEY idx_memo_owner_group (owner_username, group_id, deleted, update_time, id),
    KEY idx_memo_owner_status (owner_username, status, deleted, update_time, id),
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
    KEY idx_memo_related_permission (memo_id, related_username, deleted, id),
    KEY idx_memo_related_owner (owner_username, deleted, memo_id),
    CONSTRAINT fk_uni_memo_related_memo
        FOREIGN KEY (memo_id) REFERENCES uni_memo (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Memo 相关人表';

CREATE TABLE IF NOT EXISTS uni_memo_top (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    memo_id BIGINT NOT NULL COMMENT '置顶的 Memo ID',
    username VARCHAR(100) NOT NULL COMMENT '置顶人用户名，置顶状态按用户隔离',
    owner_username VARCHAR(100) NOT NULL COMMENT 'Memo 所有者用户名，便于所有者维度清理',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memo_top_user (memo_id, username),
    KEY idx_memo_top_user (username, deleted, memo_id),
    KEY idx_memo_top_owner (owner_username, deleted, memo_id),
    CONSTRAINT fk_uni_memo_top_memo
        FOREIGN KEY (memo_id) REFERENCES uni_memo (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Memo 用户置顶表';

CREATE TABLE IF NOT EXISTS uni_memo_favorite (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    memo_id BIGINT NOT NULL COMMENT '收藏的 Memo ID',
    username VARCHAR(100) NOT NULL COMMENT '收藏人用户名，收藏状态按用户隔离',
    owner_username VARCHAR(100) NOT NULL COMMENT 'Memo 所有者用户名，便于所有者维度清理',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memo_favorite_user (memo_id, username),
    KEY idx_memo_favorite_user (username, deleted, memo_id),
    KEY idx_memo_favorite_owner (owner_username, deleted, memo_id),
    CONSTRAINT fk_uni_memo_favorite_memo
        FOREIGN KEY (memo_id) REFERENCES uni_memo (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Memo 用户收藏表';

CREATE TABLE IF NOT EXISTS uni_user_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(128) NOT NULL COMMENT 'Windows 用户名',
    employee_no VARCHAR(32) NULL COMMENT '员工工号',
    display_name VARCHAR(128) NULL COMMENT '显示名称',
    department_name VARCHAR(128) NULL COMMENT '部门名称',
    email VARCHAR(128) NULL COMMENT '邮箱',
    source_system VARCHAR(64) NULL COMMENT '来源系统标识',
    status_snapshot VARCHAR(20) NULL COMMENT '状态快照：active=启用，inactive=停用',
    last_sync_time DATETIME NULL COMMENT '最后同步时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_snapshot_username (username),
    KEY idx_user_snapshot_employee_no (employee_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户快照表';

CREATE TABLE IF NOT EXISTS uni_auth_audit (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(128) NULL COMMENT 'Windows 用户名',
    action VARCHAR(64) NOT NULL COMMENT '认证动作：desktop_verify/token_refresh/device_verify',
    result VARCHAR(32) NOT NULL COMMENT '结果：success/fail/verification_required',
    device_id VARCHAR(128) NULL COMMENT '设备唯一标识',
    computer_name VARCHAR(128) NULL COMMENT '计算机名称',
    ip_address VARCHAR(64) NULL COMMENT '客户端IP',
    message VARCHAR(500) NULL COMMENT '审计说明',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_auth_audit_username_time (username, create_time),
    KEY idx_auth_audit_action_time (action, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='认证审计表';

CREATE TABLE IF NOT EXISTS uni_device_trust (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(128) NOT NULL COMMENT '绑定用户名',
    device_id VARCHAR(128) NOT NULL COMMENT '设备唯一标识',
    computer_name VARCHAR(128) NULL COMMENT '计算机名称',
    os VARCHAR(64) NULL COMMENT '操作系统',
    os_version VARCHAR(128) NULL COMMENT '操作系统版本',
    app_version VARCHAR(32) NULL COMMENT '应用版本',
    trust_status VARCHAR(32) NOT NULL DEFAULT 'trusted' COMMENT '信任状态：trusted=已信任，revoked=已撤销',
    first_trusted_time DATETIME NOT NULL COMMENT '首次信任时间',
    last_active_time DATETIME NOT NULL COMMENT '最后活跃时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_trust_user_device (username, device_id),
    KEY idx_device_trust_username (username, trust_status, last_active_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备信任表';

CREATE TABLE IF NOT EXISTS uni_device_verification (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    verification_id VARCHAR(64) NOT NULL COMMENT '验证码流程ID',
    username VARCHAR(128) NOT NULL COMMENT 'Windows 用户名',
    domain_name VARCHAR(128) NULL COMMENT 'Windows 域',
    device_id VARCHAR(128) NOT NULL COMMENT '设备唯一标识',
    computer_name VARCHAR(128) NULL COMMENT '计算机名称',
    os VARCHAR(64) NULL COMMENT '操作系统',
    os_version VARCHAR(128) NULL COMMENT '操作系统版本',
    app_version VARCHAR(32) NULL COMMENT '应用版本',
    code_hash VARCHAR(128) NOT NULL COMMENT '验证码哈希',
    verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已验证：0=否，1=是',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_verification_id (verification_id),
    KEY idx_device_verification_user_device (username, device_id, verified, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备验证码表';
