package dev.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "agents",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_agents_machine_uuid", columnNames = "machine_uuid")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_uuid", nullable = false, length = 36)
    private String machineUuid;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "host_name", length = 255)
    private String hostName;

    @Column(name = "cpu_name", length = 100)
    private String cpuName;

    @Column(name = "gpu_name", length = 100)
    private String gpuName;

    @Column(name = "ram_total_mb", length = 20)
    private String ramTotalMb;

    @Column(name = "os_name", length = 100)
    private String osName;

    @Column(name = "os_version", length = 100)
    private String osVersion;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}
