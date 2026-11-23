-- ============================================================
-- 51_personal_message.sql  个人消息管理模块（肖泽亮）
-- 仅建表 + 索引，不含任何演示数据；演示数据见 53_personal_message_demo.sql
-- 可重复执行：表已存在时不会删除、不会改动现有数据
--
-- 执行顺序（在目标库上依次执行）：
--   09_user_management.sql        -> 提供 todo_login_user（含 status 字段）
--   51_personal_message.sql       -> 本脚本，建 personal_message 表
--   52_personal_message_menu.sql  -> 菜单入口（增量补充个人消息菜单）
--   53_personal_message_demo.sql  -> 可选演示数据（按用户名解析账号，找不到就跳过）
--
-- 注意：脚本开头 USE 的库名与团队其他模块脚本保持一致；
--       若在验收库执行，请先确认目标库（如 greenhouse_system_test）再运行。
-- ============================================================
SET NAMES utf8mb4;
USE greenhouse_system;

CREATE TABLE IF NOT EXISTS personal_message (
                                  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
                                  title VARCHAR(200) NOT NULL COMMENT '消息标题',
                                  content TEXT COMMENT '消息正文',
                                  message_type VARCHAR(50) NOT NULL COMMENT '消息类型',
                                  sender_id INT NOT NULL COMMENT '发送人id，关联todo_login_user.id',
                                  recipient_id INT NOT NULL COMMENT '接收人id，关联todo_login_user.id',
                                  send_status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT 'draft草稿 / sent已发送 / withdrawn已撤回',
                                  sent_at DATETIME NULL COMMENT '发送时间',
                                  read_at DATETIME NULL COMMENT '已读时间，null代表未读',
                                  withdrawn_at DATETIME NULL COMMENT '撤回时间，null未撤回',
                                  recipient_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '接收人是否逻辑删除 0否1是，仅控制接收人可见',
                                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  INDEX idx_sender(sender_id),
                                  INDEX idx_recipient(recipient_id),
                                  INDEX idx_status(send_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人消息表';

-- 建表结果核对
SELECT COUNT(*) AS personal_message_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'personal_message';
