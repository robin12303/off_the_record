package dev.backend.sse.controller.dto;

public record MetricEventData(
        String timeStamp,
        int windowMs,
        Long windowEndMs,
        Long keystrokes
) {
}
