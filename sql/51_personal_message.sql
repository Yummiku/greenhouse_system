-- 51_personal_message.sql 肖泽亮 个人消息模块
-- 可重复执行：表已存在时不会删除，测试数据也不会重复插入。
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

-- 测试数据：sender_id=1是admin，recipient_id=3是worker
INSERT INTO personal_message(title, content, message_type, sender_id, recipient_id, send_status)
SELECT '测试草稿1', '这是admin写给worker的草稿', 'system', 1, 3, 'draft'
WHERE NOT EXISTS (SELECT 1 FROM personal_message WHERE title = '测试草稿1');

INSERT INTO personal_message(title, content, message_type, sender_id, recipient_id, send_status, sent_at)
SELECT '测试已发消息', 'admin发送给worker正式消息', 'notice', 1, 3, 'sent', NOW()
WHERE NOT EXISTS (SELECT 1 FROM personal_message WHERE title = '测试已发消息');
