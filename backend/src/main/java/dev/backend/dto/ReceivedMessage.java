package dev.backend.dto;

public record ReceivedMessage(
        String prefix,
        String commandId,
        String machineUuid,
        String timestamp,
        String taskType,
        String payload
) {
}
