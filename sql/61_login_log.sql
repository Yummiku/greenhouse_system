-- ============================================================
-- 61_login_log.sql  登录日志（肖泽亮 第13周）
-- 仅建表与索引，可由系统自动写入，页面不提供新增/编辑/删除入口
-- 可重复执行：CREATE TABLE IF NOT EXISTS，不改动已有数据
--
-- 执行顺序：01_greenhouse_framework.sql -> 09_user_management.sql -> 本脚本
-- 目标库：本机开发库 greenhouse_system；验收库 greenhouse_system_test
--       执行前把下方 USE 改成目标库（或客户端选中目标库后跳过 USE 行）
--
-- 安全边界：不保存密码、密码哈希、验证码、会话Cookie、微信密钥、授权令牌；
--          username_attempt 只保留去控制字符后的前64字符
-- ============================================================
SET NAMES utf8mb4;
USE greenhouse_system;

CREATE TABLE IF NOT EXISTS login_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '事件编号',
    event_time DATETIME NOT NULL COMMENT '服务端发生时间，精确到秒',
    username_attempt VARCHAR(64) NOT NULL DEFAULT '' COMMENT '尝试账号（脱敏后），未识别用户可为空',
    user_id INT NULL COMMENT '成功登录关联的真实用户ID；失败且无法识别时为空',
    result VARCHAR(16) NOT NULL COMMENT 'success 成功 / failure 失败',
    reason_code VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'credential_error 凭据错误 / account_disabled 账号停用 / invalid_input 输入不完整 / auth_service_error 认证服务失败',
    source VARCHAR(16) NOT NULL DEFAULT 'pc' COMMENT 'pc / wx / mini，仅记录已接通来源',
    ip VARCHAR(64) NOT NULL DEFAULT '' COMMENT '可信连接来源IP',
    client_info VARCHAR(128) NOT NULL DEFAULT '' COMMENT '简短客户端信息（User-Agent 截断）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
    INDEX idx_login_time(event_time),
    INDEX idx_login_user(user_id),
    INDEX idx_login_result(result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

SELECT COUNT(*) AS login_log_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'login_log';
