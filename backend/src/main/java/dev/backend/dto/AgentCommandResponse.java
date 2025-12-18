package dev.backend.dto;

public record AgentCommandResponse(
        String prefix,
        String commandId,
        String machineId,
        String taskType,
        String status
) {
}
