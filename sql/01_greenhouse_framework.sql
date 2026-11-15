-- XM06 农业大棚系统 框架数据库初始化
-- MySQL 8，目标为已经创建的空库 greenhouse_system。
-- 仅在空库执行一次；不删除表、不导入旧作业数据、不修改 demo。
-- 先执行 SHOW TABLES FROM greenhouse_system，确认结果为空。
-- 本脚本不创建登录账号，账号后续通过 Java 的 BCrypt 方法初始化。
-- 角色：1=系统管理员，2=普通管理员，3=普通用户。
SET NAMES utf8mb4;
USE greenhouse_system;

CREATE TABLE `device_file` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `parent_id` varchar(255) DEFAULT NULL,
  `device_id` varchar(255) DEFAULT '',
  `device_name` varchar(255) DEFAULT NULL,
  `device_type` varchar(255) DEFAULT NULL,
  `check_tag` int(11) DEFAULT '0',
  `checker` varchar(255) DEFAULT NULL,
  `check_time` datetime DEFAULT NULL,
  `used_tag` bit(1) DEFAULT b'0',
  `creator_id` varchar(255) DEFAULT NULL,
  `creator` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `project_todo` (
  `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
  `parent_id` varchar(255) DEFAULT NULL,
  `object_id` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT '',
  `content` varchar(255) DEFAULT '',
  `limit_time` varchar(255) DEFAULT '',
  `begin_time` varchar(255) DEFAULT NULL,
  `end_time` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `top` varchar(255) DEFAULT '',
  `creator_id` varchar(255) DEFAULT '',
  `creator` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `public_attachment` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `parent_id` varchar(255) DEFAULT NULL,
  `object_id` varchar(255) DEFAULT NULL,
  `module` varchar(255) DEFAULT NULL,
  `sub` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT '',
  `content` varchar(4096) DEFAULT '',
  `description` mediumtext,
  `attachment_name` varchar(255) DEFAULT NULL,
  `attachment_filename` varchar(255) DEFAULT NULL,
  `attachment_url` varchar(255) DEFAULT NULL,
  `attachment_size` int(10) DEFAULT NULL,
  `attachment_type` varchar(255) DEFAULT NULL,
  `view_count` int(10) DEFAULT '0',
  `comment_count` int(10) DEFAULT '0',
  `like_count` int(10) DEFAULT '0',
  `hate_count` int(10) DEFAULT '0',
  `download_count` int(10) DEFAULT '0',
  `source` varchar(255) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `category_id` varchar(255) DEFAULT '',
  `user_id` varchar(255) DEFAULT NULL,
  `user_name` varchar(255) DEFAULT NULL,
  `tags` varchar(255) DEFAULT NULL,
  `creator_id` varchar(255) DEFAULT NULL,
  `creator` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `checker` varchar(255) DEFAULT NULL,
  `check_time` varchar(255) DEFAULT NULL,
  `check_result` int(10) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `id` (`id`) USING BTREE,
  KEY `parent_id` (`parent_id`) USING BTREE,
  KEY `object_id` (`object_id`) USING BTREE,
  KEY `module` (`module`) USING BTREE,
  KEY `sub` (`sub`) USING BTREE,
  KEY `title` (`title`) USING BTREE,
  KEY `content` (`content`(255)) USING BTREE,
  KEY `attachment_name` (`attachment_name`) USING BTREE,
  KEY `attachment_filename` (`attachment_filename`) USING BTREE,
  KEY `attachment_url` (`attachment_url`) USING BTREE,
  KEY `attachment_size` (`attachment_size`) USING BTREE,
  KEY `attachment_type` (`attachment_type`) USING BTREE,
  KEY `view_count` (`view_count`) USING BTREE,
  KEY `comment_count` (`comment_count`) USING BTREE,
  KEY `like_count` (`like_count`) USING BTREE,
  KEY `hate_count` (`hate_count`) USING BTREE,
  KEY `download_count` (`download_count`) USING BTREE,
  KEY `source` (`source`) USING BTREE,
  KEY `type` (`type`) USING BTREE,
  KEY `category_id` (`category_id`) USING BTREE,
  KEY `user_id` (`user_id`) USING BTREE,
  KEY `user_name` (`user_name`) USING BTREE,
  KEY `tags` (`tags`) USING BTREE,
  KEY `creator_id` (`creator_id`) USING BTREE,
  KEY `creator` (`creator`) USING BTREE,
  KEY `create_time` (`create_time`) USING BTREE,
  KEY `checker` (`checker`) USING BTREE,
  KEY `check_time` (`check_time`) USING BTREE,
  KEY `check_result` (`check_result`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_file` (
  `id` int(10) NOT NULL AUTO_INCREMENT,
  `user_login_id` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL COMMENT '积分小类，哪种操作获得的积分',
  `creator` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 当前PC登录账号表。password只保存PasswordUtil生成的BCrypt哈希。
CREATE TABLE todo_login_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role_id INT NOT NULL COMMENT '1系统管理员，2普通管理员，3普通用户',
    PRIMARY KEY (id),
    UNIQUE KEY uk_login_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 菜单可跨模块加载，权限由menu_view与后台操作校验共同控制。
CREATE TABLE menu_tree (
    item_id INT NOT NULL,
    parent_id INT NOT NULL DEFAULT 0,
    module_id VARCHAR(64) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    item_url VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (item_id),
    KEY idx_menu_module_parent (module_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE menu_view (
    role_id INT NOT NULL,
    item_id INT NOT NULL,
    PRIMARY KEY (role_id, item_id),
    CONSTRAINT fk_menu_view_item FOREIGN KEY (item_id)
        REFERENCES menu_tree (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 移动端待办：保留与PC project_todo不同的旧表以兼容现有接口。
-- 后续正式待办统一时，应同时修改两端代码，不在此处静默合并。
CREATE TABLE todo (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    create_time DATE NOT NULL,
    remark VARCHAR(2000) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    KEY idx_todo_project_date (project_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE todo_attachment (
    attachment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    todo_id BIGINT DEFAULT NULL,
    stored_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attachment_id),
    KEY idx_todo_attachment (todo_id, created_at),
    CONSTRAINT fk_todo_attachment FOREIGN KEY (todo_id)
        REFERENCES todo (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE member_location (
    id INT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    collected_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    source VARCHAR(32) NOT NULL,
    is_simulated TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_location_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wechat_bind_ticket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bind_ticket_hash (ticket_hash),
    KEY idx_bind_ticket_user_expiry (user_id, expires_at),
    CONSTRAINT fk_bind_ticket_user FOREIGN KEY (user_id)
        REFERENCES todo_login_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信账号绑定二维码票据';

CREATE TABLE wechat_user_bind (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appid VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    openid VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL,
    identity_source VARCHAR(32) NOT NULL DEFAULT 'wechat',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bind_identity (appid, openid),
    UNIQUE KEY uk_bind_platform_user (appid, user_id),
    CONSTRAINT fk_user_bind_user FOREIGN KEY (user_id)
        REFERENCES todo_login_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信与平台用户绑定关系';

CREATE TABLE wechat_login_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    appid VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    openid VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    identity_source VARCHAR(32) NOT NULL DEFAULT 'wechat',
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_login_session_token (token_hash),
    KEY idx_login_session_identity (appid, openid),
    KEY idx_login_session_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信小程序登录会话';

-- 只初始化已经存在的页面；环境监测仍是待实现的业务骨架。
INSERT INTO menu_tree (item_id, parent_id, module_id, item_name, item_url) VALUES
(100, 0, 'todo', '工作管理', ''),
(101, 100, 'todo', '待办事项', '/maintain/todo/todo_crud.jsp'),
(102, 100, 'todo', '数据看板', '/maintain/todo/todo_dashboard.jsp'),
(200, 0, 'greenhouse', '农业管理', ''),
(201, 200, 'greenhouse', '环境监测', '/maintain/environment/environment.jsp');

INSERT INTO menu_view (role_id, item_id) VALUES
(1,100), (1,101), (1,102), (1,200), (1,201),
(2,100), (2,101), (2,102), (2,200), (2,201),
(3,100), (3,101), (3,102), (3,200), (3,201);

-- 不预置个人账号、旧作业记录或传感器测量值。
-- 导入后运行user.tools.AddUserDemo，交互式创建本机账号。
-- 应为13张表、5条菜单、15条角色菜单关系，账号表暂为空。
SHOW TABLES;
SELECT COUNT(*) AS framework_table_count FROM information_schema.TABLES
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE';
SELECT COUNT(*) AS menu_count FROM menu_tree;
SELECT COUNT(*) AS role_menu_count FROM menu_view;
SELECT COUNT(*) AS account_count FROM todo_login_user;
