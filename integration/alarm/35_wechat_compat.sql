-- Minimal compatibility patch for Dai/Xiao to review and apply to the explicit target database.
-- Existing legacy Java auth uses week08_* while framework 01 creates wechat_*.
-- Create updatable aliases only when a week08 name does not exist. Never replace existing tables/views.
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='week08_login_session')=0,
 'CREATE VIEW week08_login_session AS SELECT * FROM wechat_login_session','SELECT 1');
PREPARE alarm_compat FROM @ddl; EXECUTE alarm_compat; DEALLOCATE PREPARE alarm_compat;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='week08_user_bind')=0,
 'CREATE VIEW week08_user_bind AS SELECT * FROM wechat_user_bind','SELECT 1');
PREPARE alarm_compat FROM @ddl; EXECUTE alarm_compat; DEALLOCATE PREPARE alarm_compat;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='week08_bind_ticket')=0,
 'CREATE VIEW week08_bind_ticket AS SELECT * FROM wechat_bind_ticket','SELECT 1');
PREPARE alarm_compat FROM @ddl; EXECUTE alarm_compat; DEALLOCATE PREPARE alarm_compat;
