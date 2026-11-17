-- XM06 农业大棚系统 第12周 环境采样数据管理（唐昕煜）
-- MySQL 8；执行顺序：01_greenhouse_framework.sql → 09_user_management.sql
--               → 11_greenhouse.sql →（可选 12_device_binding_seed.sql）→ 本脚本。
-- 只新增本模块两张表与必要索引，不 DROP、不清空、不改动 01 基础初始化脚本。
-- 采样归属与指标配置来自 11_greenhouse.sql 的 greenhouse_file 与 greenhouse_device_binding，
-- 因此 11 必须先执行；否则外键创建失败（这是有意的，避免无归属采样）。
SET NAMES utf8mb4;
USE greenhouse_system;

-- 采样明细：一条记录对应“一个设备 + 一个指标 + 一个时刻”。
-- sample_key 由采集端生成并保证唯一，是上传幂等键；手工记录的键由服务端生成，前缀 fixed 为 manual。
CREATE TABLE IF NOT EXISTS `environment_sample` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `sample_key` VARCHAR(128) NOT NULL COMMENT '采集端唯一键，用于上传去重',
  `greenhouse_id` BIGINT NOT NULL COMMENT '归属大棚 greenhouse_file.id',
  `device_pk` INT UNSIGNED NOT NULL COMMENT '设备主键 device_file.id，不是业务编号',
  `metric_type` VARCHAR(32) NOT NULL COMMENT 'air_temperature|air_humidity|soil_temperature|soil_moisture|illuminance',
  `value` DECIMAL(12,3) NOT NULL COMMENT '指标数值；0 是有效数据，不等同无数据',
  `unit` VARCHAR(8) NOT NULL COMMENT '由后台按指标配置写入，不采用客户端自报单位',
  `sample_time` DATETIME NOT NULL COMMENT '采样时刻（Asia/Shanghai）',
  `received_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '服务端接收时刻',
  `source` VARCHAR(16) NOT NULL COMMENT 'real 实采 | simulated 模拟 | manual 人工',
  `created_by` BIGINT DEFAULT NULL COMMENT 'manual 记录的操作者 todo_login_user.id',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  `deleted_by` BIGINT DEFAULT NULL,
  `deleted_at` DATETIME DEFAULT NULL,
  `delete_reason` VARCHAR(255) DEFAULT NULL COMMENT '逻辑删除原因',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_environment_sample_key` (`sample_key`),
  KEY `idx_environment_query` (`greenhouse_id`, `metric_type`, `sample_time`, `id`),
  KEY `idx_environment_device` (`device_pk`, `sample_time`),
  KEY `idx_environment_source` (`source`, `sample_time`),
  KEY `idx_environment_received` (`received_at`),
  CONSTRAINT `fk_environment_greenhouse` FOREIGN KEY (`greenhouse_id`)
    REFERENCES `greenhouse_file` (`id`),
  CONSTRAINT `fk_environment_device` FOREIGN KEY (`device_pk`)
    REFERENCES `device_file` (`id`),
  CONSTRAINT `fk_environment_creator` FOREIGN KEY (`created_by`)
    REFERENCES `todo_login_user` (`id`),
  CONSTRAINT `ck_environment_metric` CHECK (`metric_type` IN
    ('air_temperature', 'air_humidity', 'soil_temperature', 'soil_moisture', 'illuminance')),
  CONSTRAINT `ck_environment_source` CHECK (`source` IN ('real', 'simulated', 'manual')),
  CONSTRAINT `ck_environment_unit` CHECK (`unit` IN ('℃', '%', 'lx')),
  CONSTRAINT `ck_environment_humidity` CHECK (
    `metric_type` NOT IN ('air_humidity', 'soil_moisture') OR (`value` >= 0 AND `value` <= 100)),
  CONSTRAINT `ck_environment_illuminance` CHECK (
    `metric_type` <> 'illuminance' OR `value` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='环境采样明细';

-- 人工更正留痕：只追加，不更新、不删除；自动上传数据不产生更正记录。
CREATE TABLE IF NOT EXISTS `environment_sample_revision` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `sample_id` BIGINT NOT NULL,
  `old_value` DECIMAL(12,3) NOT NULL,
  `new_value` DECIMAL(12,3) NOT NULL,
  `old_sample_time` DATETIME NOT NULL,
  `new_sample_time` DATETIME NOT NULL,
  `changed_by` BIGINT NOT NULL,
  `changed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `reason` VARCHAR(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `idx_environment_revision` (`sample_id`, `changed_at`),
  CONSTRAINT `fk_environment_revision_sample` FOREIGN KEY (`sample_id`)
    REFERENCES `environment_sample` (`id`),
  CONSTRAINT `fk_environment_revision_user` FOREIGN KEY (`changed_by`)
    REFERENCES `todo_login_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='人工记录更正留痕';

-- 菜单 201“环境监测”已在 01 中初始化，本模块沿用同一页面，不重复插入菜单，
-- 避免与 01/02 及其它成员脚本产生主键冲突。
SELECT COUNT(*) AS environment_table_count FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('environment_sample', 'environment_sample_revision');
SELECT COUNT(*) AS menu_environment FROM menu_tree WHERE item_id = 201;
