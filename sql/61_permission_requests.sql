-- 第13周 权限申请与审批。先执行过09用户升级，再执行本脚本。
-- 主库和验收库都可用：先在Navicat选择目标库；此脚本不写USE，避免误写主库。
-- 增量建表，不修改既有账号，不含演示账号或数据库密码。MySQL 8.0.16+。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS permission_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    applicant_id BIGINT NOT NULL,
    source_role INT NOT NULL DEFAULT 3,
    target_role INT NOT NULL DEFAULT 2,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    review_opinion VARCHAR(500) NOT NULL DEFAULT '',
    reviewer_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME NULL,
    withdrawn_at DATETIME NULL,
    version BIGINT NOT NULL DEFAULT 0,
    pending_applicant BIGINT GENERATED ALWAYS AS (CASE WHEN status='pending' THEN applicant_id ELSE NULL END) STORED,
    UNIQUE KEY uq_permission_pending (pending_applicant),
    KEY idx_permission_mine (applicant_id,created_at,id),
    KEY idx_permission_review (status,created_at,id),
    CONSTRAINT ck_permission_target CHECK (target_role=2),
    CONSTRAINT ck_permission_source CHECK (source_role=3),
    CONSTRAINT ck_permission_status CHECK (status IN ('pending','approved','rejected','withdrawn'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- 不建跨版本账号ID外键；账号沿用软删除，服务端事务验证关联身份。
SELECT DATABASE() AS target_database;
SHOW CREATE TABLE permission_request;
