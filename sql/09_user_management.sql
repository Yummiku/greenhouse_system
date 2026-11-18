-- 用户管理升级：在已有greenhouse_system基础库执行，保留账号与业务数据。
-- 可重复执行；每列独立检测，执行后需重新部署并重新登录。
SET NAMES utf8mb4;
USE greenhouse_system;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='todo_login_user' AND COLUMN_NAME='status')=0,
 'ALTER TABLE todo_login_user ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT ''active''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='todo_login_user' AND COLUMN_NAME='created_at')=0,
 'ALTER TABLE todo_login_user ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='todo_login_user' AND COLUMN_NAME='updated_at')=0,
 'ALTER TABLE todo_login_user ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='todo_login_user' AND COLUMN_NAME='auth_version')=0,
 'ALTER TABLE todo_login_user ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='todo_login_user' AND COLUMN_NAME='version')=0,
 'ALTER TABLE todo_login_user ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 不自动转换第三方已有状态值：发现非active/disabled/deleted值时先统一约定。
SELECT status, COUNT(*) AS account_count FROM todo_login_user GROUP BY status;

INSERT INTO menu_tree(item_id,parent_id,module_id,item_name,item_url) VALUES
(300,0,'identity','账号信息',''),
(301,300,'identity','用户信息','/maintain/user/index.jsp')
ON DUPLICATE KEY UPDATE item_id=VALUES(item_id);
INSERT IGNORE INTO menu_view(role_id,item_id) VALUES
(1,300),(1,301),(2,300),(2,301),(3,300),(3,301);
