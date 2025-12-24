package dev.backend.dto;

public record AgentCommandResponse(
        String prefix,
        String commandId,
        String machineUuid,
        String taskType,
        String status
) {
}
