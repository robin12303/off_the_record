-- V4__agent_metrics .sql
CREATE TABLE agent_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    machine_guid VARCHAR(64) NOT NULL,
    window_ms INT NOT NULL,

    -- 집계 구간 끝 시각 (UTC epoch ms)
    window_end_ms BIGINT NOT NULL,

    -- 집계값들 (필요한 것만)
    keystrokes BIGINT NOT NULL,
    queue_len INT NULL,
    dropped INT NULL,
    latency_p95_ms INT NULL,
    reconnects INT NULL,

    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    -- 재전송/중복 저장 방지
    UNIQUE KEY uq_agent_window (machine_guid, window_ms, window_end_ms),

    -- 조회 최적화 (그래프는 보통 시간 범위 조회)
    KEY idx_agent_time (machine_guid, window_end_ms),
    KEY idx_time (window_end_ms)
) ENGINE=InnoDB;
