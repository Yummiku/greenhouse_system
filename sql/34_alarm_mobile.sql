-- Select the explicit target database. Additive and repeatable; depends on 09 and existing week08 auth tables.
-- Records the account version at fresh WeChat login/binding, never accepts caller-supplied user IDs.
CREATE TABLE IF NOT EXISTS alarm_mobile_grant (
 token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 appid VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 user_id BIGINT NOT NULL,
 auth_version BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
