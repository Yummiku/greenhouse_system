-- 已有库先备份；依赖09、11。保留所有设备及绑定数据。
USE greenhouse_system;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 如下查询有结果，先人工核对重复编号；不要直接删除记录。
SELECT device_id,COUNT(*) duplicate_count FROM device_file
WHERE device_id IS NOT NULL GROUP BY device_id HAVING COUNT(*)>1;
-- 唯一索引由数据库防止并发新增重复编号；已有重复值时此步报1062，须核对后重试。
SET @gh_device_code_index=(SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='device_file' AND INDEX_NAME='uq_device_file_code');
SET @gh_device_code_ddl=IF(@gh_device_code_index=0,'ALTER TABLE device_file ADD UNIQUE KEY uq_device_file_code(device_id)','SELECT 1');
PREPARE gh_device_code_stmt FROM @gh_device_code_ddl;
EXECUTE gh_device_code_stmt;
DEALLOCATE PREPARE gh_device_code_stmt;
CREATE TABLE IF NOT EXISTS greenhouse_device_binding_history(
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 device_pk INT UNSIGNED NOT NULL,
 greenhouse_id BIGINT NOT NULL,
 metric_type VARCHAR(32) NOT NULL,
 unit VARCHAR(8) NOT NULL,
 unbound_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 operator_id BIGINT NOT NULL,
 KEY idx_device_unbound(device_pk,id),
 FOREIGN KEY(device_pk) REFERENCES device_file(id),
 FOREIGN KEY(greenhouse_id) REFERENCES greenhouse_file(id),
 FOREIGN KEY(operator_id) REFERENCES todo_login_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- 历史表保存解除时的原归属与操作人；不伪造旧绑定开始时间，不改采样记录。
