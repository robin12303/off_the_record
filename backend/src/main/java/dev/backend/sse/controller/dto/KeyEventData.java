package dev.backend.sse.controller.dto;

public record KeyEventData(
        String timeStamp,
        String capsLock,
        String eventType,
        String keyString

) {
}
