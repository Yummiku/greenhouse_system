-- 可选兼容：01建立wechat_*，旧AuthDao使用week08_*。已有week08表/视图则不覆盖。
-- 在组长确认采用主线01的wechat表结构后执行；视图共享原数据，不建立第二套身份。
USE greenhouse_system;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='week08_login_session'),'SELECT 1','CREATE VIEW week08_login_session AS SELECT * FROM wechat_login_session');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='week08_user_bind'),'SELECT 1','CREATE VIEW week08_user_bind AS SELECT * FROM wechat_user_bind');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF(EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='week08_bind_ticket'),'SELECT 1','CREATE VIEW week08_bind_ticket AS SELECT * FROM wechat_bind_ticket');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
