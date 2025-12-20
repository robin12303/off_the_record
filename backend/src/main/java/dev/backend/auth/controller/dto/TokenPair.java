package dev.backend.auth.controller.dto;

public record TokenPair(String accessToken, String refreshToken) {}
