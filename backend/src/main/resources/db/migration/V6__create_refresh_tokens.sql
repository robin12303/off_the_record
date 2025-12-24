-- V6__create_refresh_tokens.sql
CREATE TABLE refresh_tokens (
                                id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                user_id BIGINT UNSIGNED NOT NULL,
                                token_hash CHAR(64) NOT NULL,
                                expires_at TIMESTAMP NOT NULL,
                                revoked_at TIMESTAMP NULL,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                PRIMARY KEY (id),
                                UNIQUE KEY uk_refresh_token_hash (token_hash),
                                KEY idx_refresh_user (user_id),
                                CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
