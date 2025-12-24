-- V3__fix_agent_command_log_updated_at_and_indexes.sql
-- 목적:
-- 1) updated_at을 UPDATE 시 자동 갱신되도록 변경
-- 2) 잘못된 idx_machine_guid(command_id) 인덱스를 제거하고
--    올바른 idx_machine_guid(machine_guid) 인덱스를 생성

-- 1) updated_at 자동 갱신 설정
ALTER TABLE agent_command_log
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP;

-- 2) 잘못된 인덱스 제거 (V2에서 idx_machine_guid가 command_id에 걸려있던 상태를 정리)
DROP INDEX idx_machine_uuid ON agent_command_log;

-- 3) 올바른 인덱스 생성 (엔티티 @Index(name="idx_machine_uuid", columnList="idx_machine_uuid")와 일치)
CREATE INDEX idx_machine_uuid ON agent_command_log (machine_uuid);
