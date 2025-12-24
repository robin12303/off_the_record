package dev.backend.auth.service;

import dev.backend.auth.entity.RefreshToken;
import dev.backend.auth.repository.RefreshTokenRepository;
import dev.backend.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final long refreshTtlSeconds;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repo,
                               @Value("${app.refresh.ttlSeconds:1209600}") long refreshTtlSeconds) {
        this.repo = repo;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public record Issued(String rawToken, Instant expiresAt) {}

    @Transactional
    public Issued issue(Long userId) {
        String raw = generateToken();
        String hash = sha256Hex(raw);
        Instant exp = Instant.now().plusSeconds(refreshTtlSeconds);

        repo.save(new RefreshToken(userId, hash, exp));
        return new Issued(raw, exp);
    }

    @Transactional
    public Long validateAndRotate(String rawRefreshToken) {
        String hash = sha256Hex(rawRefreshToken);

        RefreshToken rt = repo.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Invalid refresh token"));

        if (rt.isRevoked() || rt.isExpired()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Refresh token expired/revoked");
        }

        // ✅ 회전: 기존 토큰 폐기
        rt.revokeNow();

        return rt.getUserId();
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        String hash = sha256Hex(rawRefreshToken);
        repo.findByTokenHash(hash).ifPresent(RefreshToken::revokeNow);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public void revokeAll(Long userId) {
        repo.revokeAllByUserId(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public Long validate(String rawRefreshToken) {
        String hash = sha256Hex(rawRefreshToken);

        RefreshToken rt = repo.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Invalid refresh token"));

        if (rt.getRevokedAt() != null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REVOKED_REFRESH", "Refresh token revoked");
        }
        if (rt.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "EXPIRED_REFRESH", "Refresh token expired");
        }

        return rt.getUserId();
    }
}