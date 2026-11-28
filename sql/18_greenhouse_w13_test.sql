-- 仅限独立测试项目；执行前核对前置表、字段及认证对象。
-- MySQL8；先执行09、11、14、16。仅增量，不填造旧记录时间。
USE greenhouse_system_test;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
DELIMITER //
CREATE PROCEDURE gh_w13_column(IN tabname VARCHAR(64),IN colname VARCHAR(64),IN definition VARCHAR(255))
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=tabname AND COLUMN_NAME=colname) THEN
 SET @ghddl=CONCAT('ALTER TABLE `',tabname,'` ADD COLUMN `',colname,'` ',definition);
 PREPARE ghstmt FROM @ghddl; EXECUTE ghstmt; DEALLOCATE PREPARE ghstmt;
 END IF;
END//
DELIMITER ;
CALL gh_w13_column('greenhouse_device_binding','bound_at','DATETIME NULL');
CALL gh_w13_column('greenhouse_device_binding_history','bound_at','DATETIME NULL');
CALL gh_w13_column('greenhouse_device_binding_history','device_code','VARCHAR(255) NULL');
CALL gh_w13_column('greenhouse_device_binding_history','device_name','VARCHAR(255) NULL');
CALL gh_w13_column('greenhouse_device_binding_history','greenhouse_code','VARCHAR(64) NULL');
CALL gh_w13_column('greenhouse_device_binding_history','greenhouse_name','VARCHAR(100) NULL');
CALL gh_w13_column('planting_batch','started_at','DATETIME NULL');
CALL gh_w13_column('planting_batch','harvested_at','DATETIME NULL');
CALL gh_w13_column('greenhouse_device_binding','control_purpose','VARCHAR(32) NULL');
CALL gh_w13_column('greenhouse_device_binding_history','control_purpose','VARCHAR(32) NULL');
DROP PROCEDURE gh_w13_column;
-- 旧已开始/已收获记录的未知时间保留NULL，页面标“旧记录未采集”。
