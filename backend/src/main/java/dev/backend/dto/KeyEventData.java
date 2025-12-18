package dev.backend.dto;

public record KeyEventData(
        String timeStamp,
        String capsLock,
        String eventType,
        String keyString

) {
}
