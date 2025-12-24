-- V2__create_agent_command_log.sql
CREATE TABLE agent_command_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prefix VARCHAR(50) NOT NULL,
    command_id VARCHAR(50) NOT NULL,
    machine_uuid CHAR(36) NOT NULL,
    task_type VARCHAR(20) NOT NULL,

    status VARCHAR(20) DEFAULT 'PENDING',
    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

        PRIMARY KEY (id),

        UNIQUE KEY uq_agent_command_id (command_id),

        KEY idx_machine_uuid (machine_uuid),
        KEY idx_machine_uuid_updated_at (machine_uuid, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
