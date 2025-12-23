package dev.backend.dto;

public record MetricEventData(
        String timeStamp,
        Long windowMs,
        Long windowEndMs,
        Long keystrokes
) {
}
