package dev.backend.repository;

import dev.backend.entity.AgentCommandLog;
import dev.backend.entity.AgentMetric;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentMetricsRepository extends JpaRepository<AgentMetric, Long> {

    @Modifying
    @Transactional
    @Query(value = """
    INSERT INTO agent_metrics
    (machine_uuid, window_ms, window_end_ms, keystrokes, queue_len, dropped, latency_p95_ms, reconnects)
    VALUES (:machineUuid, :windowMs, :windowEndMs, :keystrokes, :queueLen, :dropped, :latencyP95Ms, :reconnects)
    """, nativeQuery = true)
    int insertMetric(
            @Param("machineUuid") String machineUuid,
            @Param("windowMs") int windowMs,
            @Param("windowEndMs") long windowEndMs,
            @Param("keystrokes") long keystrokes,
            @Param("queueLen") Integer queueLen,
            @Param("dropped") Integer dropped,
            @Param("latencyP95Ms") Integer latencyP95Ms,
            @Param("reconnects") Integer reconnects
    );
    @Modifying
    @Transactional
    @Query(value = """
    INSERT INTO agent_metrics
    (machine_uuid, window_ms, window_end_ms, keystrokes)
    VALUES (:machineUuid, :windowMs, :windowEndMs, :keystrokes)
    """, nativeQuery = true)
    int insertRequiredOnly(
            @Param("machineUuid") String machineUuid,
            @Param("windowMs") int windowMs,
            @Param("windowEndMs") long windowEndMs,
            @Param("keystrokes") long keystrokes
    );

}
