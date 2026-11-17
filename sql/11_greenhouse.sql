-- MySQL 8；先执行项目基础初始化及账号工具。已有库不得重跑01。
USE greenhouse_system;
CREATE TABLE IF NOT EXISTS greenhouse_file (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 greenhouse_code VARCHAR(64) NOT NULL UNIQUE,
 name VARCHAR(100) NOT NULL, location VARCHAR(255) NOT NULL,
 area DECIMAL(12,2) NOT NULL CHECK(area>0),
 length DECIMAL(12,2) NOT NULL CHECK(length>0),
 width DECIMAL(12,2) NOT NULL CHECK(width>0),
 manager_id BIGINT NOT NULL, status VARCHAR(16) NOT NULL,
 remark VARCHAR(1000) NOT NULL DEFAULT '',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 is_deleted TINYINT NOT NULL DEFAULT 0,
 FOREIGN KEY(manager_id) REFERENCES todo_login_user(id),
 CHECK(status IN ('enabled','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 全组统一关联表：设备必须先有档案；主键与业务编号不同。
CREATE TABLE IF NOT EXISTS greenhouse_device_binding (
 device_pk INT UNSIGNED PRIMARY KEY,
 greenhouse_id BIGINT NOT NULL,
 metric_type VARCHAR(32) NOT NULL, unit VARCHAR(8) NOT NULL,
 FOREIGN KEY(device_pk) REFERENCES device_file(id),
 FOREIGN KEY(greenhouse_id) REFERENCES greenhouse_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
