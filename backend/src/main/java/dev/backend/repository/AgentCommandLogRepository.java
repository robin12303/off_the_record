package dev.backend.repository;

import dev.backend.entity.AgentCommandLog;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface AgentCommandLogRepository extends JpaRepository<AgentCommandLog, Long> {

    @Modifying
    @Transactional
    @Query(value = """
    INSERT INTO agent_command_log (
        prefix, command_id, machine_guid, task_type, status
    ) VALUES (
        :prefix, :commandId, :machineGuid, :taskType, :status
    )
    ON DUPLICATE KEY UPDATE
        prefix       = VALUES(prefix),
        machine_guid = VALUES(machine_guid),
        task_type    = VALUES(task_type),
        status       = VALUES(status)
    """, nativeQuery = true)
    int upsertByCommandId(
            @Param("prefix") String prefix,
            @Param("commandId") String commandId,
            @Param("machineGuid") String machineGuid,
            @Param("taskType") String taskType,
            @Param("status") String status
    );
}
