CREATE DATABASE IF NOT EXISTS unicomm
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE unicomm;

CREATE TABLE IF NOT EXISTS uni_memo_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_username VARCHAR(100) NOT NULL,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(32) NOT NULL DEFAULT '#6B7280',
    icon VARCHAR(64) NOT NULL DEFAULT 'folder',
    sort_order INT NOT NULL DEFAULT 0,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_memo_group_owner (owner_username, deleted, sort_order),
    KEY idx_memo_group_default (owner_username, is_default, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS uni_memo (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_username VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NULL,
    group_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'normal',
    is_top TINYINT(1) NOT NULL DEFAULT 0,
    is_favorite TINYINT(1) NOT NULL DEFAULT 0,
    is_archived TINYINT(1) NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    deleted_time DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_memo_owner_list (owner_username, deleted, is_archived, is_top, update_time),
    KEY idx_memo_owner_group (owner_username, group_id, deleted),
    KEY idx_memo_owner_favorite (owner_username, is_favorite, deleted),
    CONSTRAINT fk_uni_memo_group
        FOREIGN KEY (group_id) REFERENCES uni_memo_group (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
