package dev.backend.auth.controller.dto;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {}