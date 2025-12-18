-- V1__create_agents.sql
CREATE TABLE agents
(
    id BIGINT NOT NULL AUTO_INCREMENT,      -- 내부 PK
    machine_guid CHAR(36) NOT NULL,         -- 장비 UUID
    ip_address VARCHAR(45),                 -- IPv4/IPv6 (의미: observed or reported로 고정)

    host_name VARCHAR(255),                  -- 장비 이름
    cpu_name VARCHAR(100),                  -- CPU 모델명
    gpu_name VARCHAR(100),                  -- GPU 모델명(없으면 NULL)
    ram_total_mb VARCHAR(20),                       -- RAM 총량(MB)
    os_name VARCHAR(100),                   -- OS 이름
    os_version VARCHAR(100),                -- OS 버전
    last_seen_at DATETIME NOT NULL,         -- 마지막 보고 시각

    PRIMARY KEY (id),
    UNIQUE KEY uq_agents_machine_guid (machine_guid)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;