package dev.backend.auth.controller.dto;

public record IssuedTokens(
        String accessToken,
        long accessExpiresInSeconds,
        String refreshTokenRaw
) {}