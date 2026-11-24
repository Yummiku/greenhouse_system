-- ============================================================
-- 62_operation_log.sql  操作日志（肖泽亮 第13周，公共记录方法）
-- 由业务写入口调用 OperationLogger 自动写入；页面不提供任意新增/修改/删除
-- 可重复执行：CREATE TABLE IF NOT EXISTS
--
-- 执行顺序：61_login_log.sql -> 本脚本
-- 目标库：greenhouse_system（验收库 greenhouse_system_test，执行前改 USE）
--
-- 安全边界：只写关键非敏感字段；密码重置只记“发生过”，不记新旧密码或哈希；
--          正文/配置不整段入库，摘要限制 1000 字符
-- ============================================================
SET NAMES utf8mb4;
USE greenhouse_system;

CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志编号',
    operate_time DATETIME NOT NULL COMMENT '服务端操作时间，精确到秒',
    operator_id INT NULL COMMENT '操作人用户ID，取自会话；未知为空',
    operator_name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '操作时显示名快照',
    role_id INT NULL COMMENT '操作时角色快照 1/2/3',
    module VARCHAR(50) NOT NULL DEFAULT '' COMMENT '业务模块，如 用户管理/个人消息',
    action VARCHAR(50) NOT NULL DEFAULT '' COMMENT '动作，如 新建账号/发送消息',
    target_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '目标记录编号',
    target_name VARCHAR(200) NOT NULL DEFAULT '' COMMENT '关联业务名称（可为空）',
    result VARCHAR(16) NOT NULL COMMENT 'success 成功 / failed 失败 / denied 拒绝',
    reason_code VARCHAR(32) NOT NULL DEFAULT '' COMMENT '安全业务类别，如 no_permission/validation_failed',
    summary VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '关键非敏感字段变更摘要',
    trace_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '请求或业务追踪标识，用于排查与去重',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
    INDEX idx_op_time(operate_time),
    INDEX idx_op_operator(operator_id),
    INDEX idx_op_module(module),
    INDEX idx_op_result(result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

SELECT COUNT(*) AS operation_log_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log';
