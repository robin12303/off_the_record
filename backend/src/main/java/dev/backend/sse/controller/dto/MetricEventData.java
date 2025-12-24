package dev.backend.sse.controller.dto;

public record MetricEventData(
        String timeStamp,
        Long windowMs,
        Long windowEndMs,
        Long keystrokes
) {
}
