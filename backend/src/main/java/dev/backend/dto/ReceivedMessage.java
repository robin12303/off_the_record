package dev.backend.dto;

public record ReceivedMessage(
        String prefix,
        String commandId,
        String machineGuid,
        String timestamp,
        String taskType,
        String payload
) {
}
