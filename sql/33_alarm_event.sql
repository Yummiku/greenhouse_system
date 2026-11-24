-- MySQL8, select the target database explicitly before execution.
-- Dependencies: 09 users, 11 greenhouses/devices, 21 environment samples, 31 thresholds.
-- Additive and repeatable. No USE, DROP, sample backfill or reset of acceptance time.
CREATE TABLE IF NOT EXISTS alarm_runtime (
 id TINYINT PRIMARY KEY,
 accept_from DATETIME(6) NOT NULL,
 CHECK(id=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alarm_stream (
 greenhouse_id BIGINT NOT NULL,
 metric_type VARCHAR(40) NOT NULL,
 watermark_time DATETIME(6) NULL,
 watermark_sample_id BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(greenhouse_id,metric_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alarm_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 greenhouse_id BIGINT NOT NULL,
 greenhouse_name VARCHAR(100) NOT NULL,
 greenhouse_code VARCHAR(64) NOT NULL,
 metric_type VARCHAR(40) NOT NULL,
 unit VARCHAR(16) NOT NULL,
 source VARCHAR(16) NOT NULL,
 direction VARCHAR(8) NOT NULL,
 first_sample_id BIGINT NOT NULL,
 first_device_pk INT UNSIGNED NOT NULL,
 first_device_code VARCHAR(255) NOT NULL,
 first_sample_value DECIMAL(18,6) NOT NULL,
 first_triggered_at DATETIME(6) NOT NULL,
 first_rule_id BIGINT NOT NULL,
 first_lower DECIMAL(18,6) NULL,
 first_upper DECIMAL(18,6) NULL,
 last_sample_id BIGINT NOT NULL,
 last_device_pk INT UNSIGNED NOT NULL,
 last_source VARCHAR(16) NOT NULL,
 last_direction VARCHAR(8) NOT NULL,
 last_sample_value DECIMAL(18,6) NOT NULL,
 last_triggered_at DATETIME(6) NOT NULL,
 last_rule_id BIGINT NOT NULL,
 last_lower DECIMAL(18,6) NULL,
 last_upper DECIMAL(18,6) NULL,
 abnormal_count BIGINT NOT NULL DEFAULT 1,
 status VARCHAR(16) NOT NULL DEFAULT 'pending',
 invalid_reason VARCHAR(255) NULL,
 confirmed_by BIGINT NULL,
 confirmed_name VARCHAR(100) NULL,
 confirmed_at DATETIME(6) NULL,
 closed_by BIGINT NULL,
 closed_name VARCHAR(100) NULL,
 closed_at DATETIME(6) NULL,
 close_reason VARCHAR(16) NULL,
 close_note VARCHAR(1000) NULL,
 recovery_sample_id BIGINT NULL,
 recovery_value DECIMAL(18,6) NULL,
 recovery_time DATETIME(6) NULL,
 recovery_rule_id BIGINT NULL,
 recovery_lower DECIMAL(18,6) NULL,
 recovery_upper DECIMAL(18,6) NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 active_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status<>'closed' THEN 1 ELSE NULL END) STORED,
 UNIQUE KEY uq_alarm_active(greenhouse_id,metric_type,active_slot),
 KEY idx_alarm_query(first_triggered_at,id),
 CHECK(status IN ('pending','processing','closed')),
 CHECK(direction IN ('low','high')),
 CHECK(source IN ('real','simulated','manual')),
 CHECK(last_source IN ('real','simulated','manual')),
 CHECK(abnormal_count>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alarm_sample_task (
 sample_id BIGINT PRIMARY KEY,
 greenhouse_id BIGINT NOT NULL,
 metric_type VARCHAR(40) NOT NULL,
 sample_time DATETIME(6) NOT NULL,
 state VARCHAR(12) NOT NULL DEFAULT 'pending',
 outcome VARCHAR(32) NULL,
 event_id BIGINT NULL,
 attempts INT NOT NULL DEFAULT 0,
 failures INT NOT NULL DEFAULT 0,
 next_retry_at DATETIME(6) NOT NULL,
 last_error_code VARCHAR(48) NULL,
 last_manual_retry_by BIGINT NULL,
 last_manual_retry_at DATETIME(6) NULL,
 manual_retries INT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 completed_at DATETIME(6) NULL,
 KEY idx_alarm_due(state,next_retry_at,sample_time,sample_id),
 KEY idx_alarm_stream_task(greenhouse_id,metric_type,state,sample_time,sample_id),
 CHECK(state IN ('pending','done','failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alarm_action (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 event_id BIGINT NOT NULL,
 action VARCHAR(24) NOT NULL,
 request_key VARCHAR(128) NOT NULL,
 actor_id BIGINT NULL,
 actor_name VARCHAR(100) NOT NULL,
 note VARCHAR(1000) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uq_alarm_action_request(event_id,request_key),
 KEY idx_alarm_action_event(event_id,id),
 CONSTRAINT fk_alarm_action_event FOREIGN KEY(event_id) REFERENCES alarm_event(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
