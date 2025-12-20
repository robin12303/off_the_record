package dev.backend.auth.service;


import dev.backend.auth.controller.dto.LoginRequest;
import dev.backend.auth.controller.dto.SigninRequest;
import dev.backend.auth.controller.dto.TokenPair;
import dev.backend.auth.entity.RefreshToken;
import dev.backend.auth.entity.User;
import dev.backend.auth.entity.enums.Role;
import dev.backend.auth.entity.enums.Status;
import dev.backend.auth.jwt.JwtProvider;
import dev.backend.auth.repository.RefreshTokenRepository;
import dev.backend.auth.repository.UserRepository;
import dev.backend.util.RefreshTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Service
public class AuthService {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom RNG = new SecureRandom();
    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    public AuthService(JwtProvider jwtProvider,
                       UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public TokenPair login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        return issueTokens(user); // 토큰 발급은 기존 로직 재사용
    }

    @Transactional
    public TokenPair signin(SigninRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = new User();
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        user.setStatus(Status.ACTIVE);

        userRepository.save(user);

        // 가입 성공하면 토큰 발급 (access + refresh)
        return issueTokens(user);
    }

    public TokenPair issueTokens(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        return issueTokens(user);
    }

    // 내부 구현은 private로 유지
    private TokenPair issueTokens(User user) {
        String access = jwtProvider.createAccessToken(
                user.getEmail(),
                Map.of("role", user.getRole().name())
        );

        // refresh 발급/저장 ...
        return new TokenPair(access, "refreshRaw");
    }

    @Transactional
    public TokenPair refresh(String refreshRaw) {
        String hash = sha256Hex(refreshRaw);

        RefreshToken rt = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(rt);
            throw new IllegalArgumentException("Expired refresh token");
        }

        // refresh rotation: 새 refresh로 교체
        String newRefreshRaw = newRefreshToken();
        rt.setTokenHash(sha256Hex(newRefreshRaw));
        rt.setExpiresAt(LocalDateTime.now().plusSeconds(REFRESH_TTL.toSeconds()));

        String newAccess = jwtProvider.createAccessToken(
                rt.getUser().getEmail(),
                Map.of("role", rt.getUser().getRole().name())
        );

        return new TokenPair(newAccess, newRefreshRaw);
    }

    @Transactional
    public void logout(String refreshRaw) {
        String hash = sha256Hex(refreshRaw);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(refreshTokenRepository::delete);
    }

    private static String newRefreshToken() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte x : dig) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
