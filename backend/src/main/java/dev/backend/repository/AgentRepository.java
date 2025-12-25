package dev.backend.repository;

import dev.backend.entity.Agent;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {


    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO agents (
            machine_uuid, ip_address, host_name, cpu_name, gpu_name,
            ram_total_mb, os_name, os_version, last_seen_at
        ) VALUES (
            :machineUuid, :ipAddress, :hostName, :cpuName, :gpuName,
            :ramTotalMb, :osName, :osVersion, :lastSeenAt
        )
        ON DUPLICATE KEY UPDATE
            ip_address   = VALUES(ip_address),
            host_name     = VALUES(host_name),
            cpu_name     = VALUES(cpu_name),
            gpu_name     = VALUES(gpu_name),
            ram_total_mb = VALUES(ram_total_mb),
            os_name      = VALUES(os_name),
            os_version   = VALUES(os_version),
            last_seen_at = VALUES(last_seen_at)
        """, nativeQuery = true)
    void upsertByMachineUuid(
            @Param("machineUuid") String machineUuid,
            @Param("ipAddress") String ipAddress,
            @Param("hostName") String hostName,
            @Param("cpuName") String cpuName,
            @Param("gpuName") String gpuName,
            @Param("ramTotalMb") String ramTotalMb,
            @Param("osName") String osName,
            @Param("osVersion") String osVersion,
            @Param("lastSeenAt") LocalDateTime lastSeenAt
    );
    Optional<Agent> findByMachineUuid(String machineUuid);

    // 확장성 좋은 버전
    Page<Agent> findAllByOrderByLastSeenAtDesc(Pageable pageable);
}