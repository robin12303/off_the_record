package dev.backend.dto;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        Instant timestamp
) {}