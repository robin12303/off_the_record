package dev.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "agent_command_log",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_agent_command_id", columnNames = "command_id")
        },
        indexes = {
                @Index(name = "idx_machine_guid", columnList = "machine_guid"),
                @Index(name = "idx_machine_guid_updated_at", columnList = "machine_guid, updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentCommandLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prefix", nullable = false, length = 50)
    private String prefix;

    @Column(name = "command_id", nullable = false, length = 50)
    private String commandId;

    @Column(name = "machine_guid", nullable = false, length = 36)
    private String machineGuid;

    @Column(name = "task_type", nullable = false, length = 20)
    private String taskType; // 'TASK', 'CONFIG', 'HEARTBEAT' 같은 값

    @Column(name = "status", length = 20)
    private String status = "PENDING"; // DB default랑 맞춤

    // DB default CURRENT_TIMESTAMP 사용
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
