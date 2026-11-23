CREATE TABLE IF NOT EXISTS threshold_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_name VARCHAR(100) NOT NULL,
    greenhouse_id BIGINT NOT NULL,
    metric_type VARCHAR(40) NOT NULL,
    lower_limit DECIMAL(18,6) NULL,
    upper_limit DECIMAL(18,6) NULL,
    unit VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    remark VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    active_slot TINYINT GENERATED ALWAYS AS (
        CASE WHEN enabled = 1 AND is_deleted = 0 THEN 1 ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_threshold_active (greenhouse_id, metric_type, active_slot),
    CONSTRAINT chk_threshold_bounds CHECK (
        (lower_limit IS NOT NULL OR upper_limit IS NOT NULL)
        AND (lower_limit IS NULL OR upper_limit IS NULL OR lower_limit < upper_limit)
    ),
    CONSTRAINT chk_threshold_flags CHECK (enabled IN (0, 1) AND is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
