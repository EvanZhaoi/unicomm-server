-- UniComm Server 数据库初始化脚本
-- Phase 1: 认证会话表 + 用户快照表（缓存和审计用，非主数据）
-- Phase 2+ 将扩展更多业务表

-- 创建数据库 (如不存在)
-- CREATE DATABASE IF NOT EXISTS unicomm DEFAULT CHARSET utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- USE unicomm;

-- =====================================================
-- 认证会话表
-- 用于会话管理和审计
-- =====================================================
CREATE TABLE IF NOT EXISTS `uni_auth_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  
  -- 会话信息
  `session_token` VARCHAR(256) NOT NULL COMMENT '会话 Token',
  `username` VARCHAR(128) NOT NULL COMMENT 'Windows 用户名',
  `employee_no` VARCHAR(32) DEFAULT NULL COMMENT '员工工号',
  `device_id` VARCHAR(128) DEFAULT NULL COMMENT '设备标识',
  `computer_name` VARCHAR(64) DEFAULT NULL COMMENT '计算机名',
  
  -- 状态和时间
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态: 0=失效 1=有效',
  `expires_time` DATETIME DEFAULT NULL COMMENT '过期时间',
  `last_active_time` DATETIME DEFAULT NULL COMMENT '最后活跃时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_session_token` (`session_token`),
  INDEX `idx_username` (`username`),
  INDEX `idx_status_expires` (`status`, `expires_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认证会话表';

-- =====================================================
-- 用户快照表（缓存/审计用，非主数据）
-- 人员真实信息以公司人员接口返回结果为准
-- =====================================================
CREATE TABLE IF NOT EXISTS `uni_user_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  
  -- 用户信息（从公司人员接口同步）
  `username` VARCHAR(128) NOT NULL COMMENT 'Windows 用户名',
  `employee_no` VARCHAR(32) DEFAULT NULL COMMENT '员工工号',
  `display_name` VARCHAR(128) DEFAULT NULL COMMENT '显示名称',
  `department_name` VARCHAR(128) DEFAULT NULL COMMENT '部门名称',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  
  -- 来源和状态
  `source_system` VARCHAR(64) DEFAULT NULL COMMENT '来源系统标识',
  `status_snapshot` VARCHAR(20) DEFAULT NULL COMMENT '状态快照: active/inactive',
  
  -- 同步信息
  `last_sync_time` DATETIME DEFAULT NULL COMMENT '最后同步时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次同步时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_username` (`username`),
  INDEX `idx_employee_no` (`employee_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户快照表（非主数据，仅缓存和审计）';

-- =====================================================
-- 设备信任表（可选功能）
-- 用于安全审计和设备白名单
-- =====================================================
CREATE TABLE IF NOT EXISTS `uni_device_trust` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  
  -- 设备信息
  `device_id` VARCHAR(128) NOT NULL COMMENT '设备唯一标识',
  `username` VARCHAR(128) NOT NULL COMMENT '绑定的用户名',
  `computer_name` VARCHAR(64) DEFAULT NULL COMMENT '计算机名',
  `os` VARCHAR(32) DEFAULT NULL COMMENT '操作系统',
  `os_version` VARCHAR(64) DEFAULT NULL COMMENT '系统版本',
  `app_version` VARCHAR(16) DEFAULT NULL COMMENT '应用版本',
  
  -- 信任状态
  `trust_level` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '信任级别: 0=未验证 1=已验证 2=信任',
  `last_active_time` DATETIME DEFAULT NULL COMMENT '最后活跃时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次登记时间',
  
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_device_id` (`device_id`),
  INDEX `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备信任表';

-- =====================================================
-- Memo 相关表（Phase 1 实现）
-- =====================================================

-- 备忘录主表
CREATE TABLE IF NOT EXISTS `uni_memo` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  
  -- 内容字段
  `title` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '标题',
  `content` TEXT COMMENT 'Markdown 内容',
  
  -- 组织和状态
  `group_id` BIGINT NOT NULL DEFAULT 0 COMMENT '分组ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT '状态: normal/todo/done',
  `is_top` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶: 0-否 1-是',
  `is_favorite` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收藏: 0-否 1-是',
  `is_archived` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否归档: 0-否 1-是',
  
  -- 数据归属（使用 username 而非 user_id）
  `owner_username` VARCHAR(128) NOT NULL COMMENT '所有者用户名（用于数据隔离）',
  `owner_employee_no` VARCHAR(32) DEFAULT NULL COMMENT '所有者工号（快照）',
  `owner_display_name` VARCHAR(128) DEFAULT NULL COMMENT '所有者显示名称（快照）',
  
  -- 审计字段
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删 1-已删',
  `deleted_time` DATETIME DEFAULT NULL COMMENT '删除时间',
  
  PRIMARY KEY (`id`),
  -- 列表查询索引（使用 owner_username）
  INDEX `idx_owner_list` (`owner_username`, `deleted`, `is_archived`, `group_id`, `is_top`, `update_time`),
  INDEX `idx_owner_status` (`owner_username`, `status`),
  INDEX `idx_owner_favorite` (`owner_username`, `is_favorite`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备忘录主表';

-- 备忘录分组表
CREATE TABLE IF NOT EXISTS `uni_memo_group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  
  -- 分组信息
  `name` VARCHAR(50) NOT NULL COMMENT '分组名称',
  `color` VARCHAR(20) NOT NULL DEFAULT '#6B7280' COMMENT '颜色(HEX)',
  `icon` VARCHAR(50) NOT NULL DEFAULT '📁' COMMENT '图标(emoji)',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  
  -- 数据归属
  `owner_username` VARCHAR(128) NOT NULL COMMENT '所有者用户名（用于数据隔离）',
  
  -- 审计字段
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认分组: 0-否 1-是',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删 1-已删',
  
  PRIMARY KEY (`id`),
  INDEX `idx_owner_default` (`owner_username`, `is_default`, `deleted`),
  INDEX `idx_owner_sort` (`owner_username`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备忘录分组表';

-- 备忘录标签表
CREATE TABLE IF NOT EXISTS `uni_memo_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  
  -- 标签信息
  `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
  `color` VARCHAR(20) NOT NULL DEFAULT '#6B7280' COMMENT '颜色(HEX)',
  
  -- 数据归属
  `owner_username` VARCHAR(128) NOT NULL COMMENT '所有者用户名（用于数据隔离）',
  
  -- 审计字段
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删 1-已删',
  
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_owner_name` (`owner_username`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备忘录标签表';

-- 备忘录-标签关联表
CREATE TABLE IF NOT EXISTS `uni_memo_tag_rel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  
  -- 关联信息
  `memo_id` BIGINT NOT NULL COMMENT 'Memo ID',
  `tag_id` BIGINT NOT NULL COMMENT 'Tag ID',
  
  -- 审计字段
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_memo_tag` (`memo_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备忘录-标签关联表';

-- =====================================================
-- 默认分组初始化
-- =====================================================

-- 为测试用户初始化默认分组
INSERT INTO `uni_memo_group` (`name`, `owner_username`, `is_default`)
VALUES 
  ('我的备忘', 'evan.zhao', 1),
  ('我的备忘', 'alice.wang', 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);