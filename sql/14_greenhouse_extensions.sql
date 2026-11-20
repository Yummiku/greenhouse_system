-- MySQL8，顺序09→11→12（可选）→14；不改现有主键与账号。
USE greenhouse_system;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS greenhouse_geo (
 greenhouse_id BIGINT PRIMARY KEY,
 latitude DECIMAL(9,6) NOT NULL CHECK(latitude BETWEEN -90 AND 90),
 longitude DECIMAL(10,6) NOT NULL CHECK(longitude BETWEEN -180 AND 180),
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY(greenhouse_id) REFERENCES greenhouse_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS planting_batch (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 greenhouse_id BIGINT NOT NULL,
 batch_code VARCHAR(64) NOT NULL UNIQUE,
 crop_name VARCHAR(100) NOT NULL,
 variety VARCHAR(100) NOT NULL,
 planting_date DATE NOT NULL,
 growth_days INT NOT NULL CHECK(growth_days>0),
 status VARCHAR(16) NOT NULL CHECK(status IN ('planned','growing','harvested','canceled')),
 remark VARCHAR(1000) NOT NULL DEFAULT '',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 is_deleted TINYINT NOT NULL DEFAULT 0,
 FOREIGN KEY(greenhouse_id) REFERENCES greenhouse_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- 设备绑定不允许直接覆盖已有绑定；有历史采样时不开放迁移/解绑，以保留归属。
