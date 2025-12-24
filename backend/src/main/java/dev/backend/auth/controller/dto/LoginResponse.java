package dev.backend.auth.controller.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Long id,
        String email
) {}