package dev.backend.dto;

import java.time.Instant;
import java.util.UUID;

// 요청 DTO
public record AgentCommandRequest(
        String prefix,
        String commandId,
        String machineGuid,
        String taskType  // "START_READ", "STOP_READ"

) {
    public static AgentCommandRequest readStop(String machineGuid, String commandId) {
        return new AgentCommandRequest("READ", commandId, machineGuid, "STOP");
    }
    public static AgentCommandRequest readStart(String machineGuid, String commandId) {
        return new AgentCommandRequest("READ", commandId, machineGuid, "START");
    }
    public static AgentCommandRequest metricsStart(String machineGuid, String commandId) {
        return new AgentCommandRequest("METRICS", commandId, machineGuid, "START");
    }
    public static AgentCommandRequest metricsStop(String machineGuid, String commandId) {
        return new AgentCommandRequest("METRICS", commandId, machineGuid, "STOP");
    }
    public static AgentCommandRequest Hello() {
        return new AgentCommandRequest("HEARTBEAT", "HEARTBEAT", "HEARTBEAT", "OK");
    }
}