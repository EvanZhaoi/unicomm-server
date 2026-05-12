-- UniComm Server 数据库初始化脚本
-- Phase 1: 仅包含员工用户表
-- Phase 2+ 将扩展更多表结构

-- 创建数据库 (如不存在)
-- CREATE DATABASE IF NOT EXISTS unicomm DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE unicomm;

-- 员工用户表
CREATE TABLE IF NOT EXISTS `uni_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `employee_no` VARCHAR(32) NOT NULL COMMENT '员工编号',
  `display_name` VARCHAR(64) NOT NULL COMMENT '显示名称',
  `department_id` BIGINT DEFAULT NULL COMMENT '部门ID',
  `department_name` VARCHAR(128) DEFAULT NULL COMMENT '部门名称',
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态: 0=禁用, 1=启用',
  `windows_username` VARCHAR(128) NOT NULL COMMENT 'Windows 用户名',
  `windows_domain` VARCHAR(128) DEFAULT NULL COMMENT 'Windows 域',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_employee_no` (`employee_no`),
  UNIQUE INDEX `uk_windows_user` (`windows_domain`, `windows_username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工用户表';

-- 种子数据: 测试用户
INSERT INTO `uni_user` (`employee_no`, `display_name`, `department_id`, `department_name`, `status`, `windows_username`, `windows_domain`)
VALUES
  ('E10001', 'Evan Zhao', 1, 'IT Department', 1, 'evan.zhao', 'COMPANY'),
  ('E10002', 'Alice Wang', 2, 'HR Department', 1, 'alice.wang', 'COMPANY'),
  ('E10003', 'Bob Li', 1, 'IT Department', 0, 'bob.li', 'COMPANY')  -- 禁用用户
ON DUPLICATE KEY UPDATE
  `display_name` = VALUES(`display_name`),
  `status` = VALUES(`status`);
