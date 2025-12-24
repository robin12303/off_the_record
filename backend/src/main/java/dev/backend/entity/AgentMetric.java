package dev.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "agent_metrics",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_agent_window",
                        columnNames = {"agent_id", "window_ms", "window_end_ms"}
                )
        },
        indexes = {
                @Index(name = "idx_agent_time", columnList = "agent_id, window_end_ms"),
                @Index(name = "idx_time", columnList = "window_end_ms")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_uuid", nullable = false, length = 64)
    private String machineUuid;

    @Column(name = "window_ms", nullable = false)
    private Integer windowMs;

    // 집계 구간 끝 시각 (UTC epoch ms)
    @Column(name = "window_end_ms", nullable = false)
    private Long windowEndMs;

    @Column(name = "keystrokes", nullable = false)
    private Long keystrokes;

    @Column(name = "queue_len")
    private Integer queueLen;

    @Column(name = "dropped")
    private Integer dropped;

    @Column(name = "latency_p95_ms")
    private Integer latencyP95Ms;

    @Column(name = "reconnects")
    private Integer reconnects;

    // DB DEFAULT CURRENT_TIMESTAMP(3) 쓰는 방식: INSERT에서 제외해서 DB가 채우게 함
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}