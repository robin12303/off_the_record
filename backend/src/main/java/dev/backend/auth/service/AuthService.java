package dev.backend.auth.service;

import dev.backend.auth.controller.dto.IssuedTokens;
import dev.backend.auth.controller.dto.LoginRequest;
import dev.backend.auth.controller.dto.SignupRequest;
import dev.backend.auth.entity.User;
import dev.backend.auth.repository.UserRepository;
import dev.backend.exception.ApiException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    @Transactional
    public IssuedTokens signup(SignupRequest req) {
        String email = req.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email already exists");
        }

        String hash = passwordEncoder.encode(req.password());
        User saved = userRepository.save(new User(email, hash));

        String access = jwtService.issueAccessToken(saved.getId(), saved.getEmail());
        var refreshIssued = refreshTokenService.issue(saved.getId()); // (rawToken, expiresAt)

        return new IssuedTokens(access, jwtService.getAccessTtlSeconds(), refreshIssued.rawToken());
    }

    @Transactional
    public IssuedTokens login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password"
                ));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password"
            );
        }

        String access = jwtService.issueAccessToken(user.getId(), user.getEmail());
        var refreshIssued = refreshTokenService.issue(user.getId());

        return new IssuedTokens(access, jwtService.getAccessTtlSeconds(), refreshIssued.rawToken());
    }
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return; // 이미 로그아웃 상태처럼 취급
        }
        refreshTokenService.revoke(rawRefreshToken);
    }
    @Transactional
    public IssuedTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MISSING_REFRESH", "Refresh token missing");
        }

        // 1) 기존 refresh 검증 + 회전(기존 토큰 revoked 처리)
        Long userId = refreshTokenService.validateAndRotate(rawRefreshToken);

        // 2) 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "User not found"));

        // 3) 새 access 발급
        String access = jwtService.issueAccessToken(user.getId(), user.getEmail());

        // 4) 새 refresh 발급 (회전)
        var newRefresh = refreshTokenService.issue(userId);

        return new IssuedTokens(access, jwtService.getAccessTtlSeconds(), newRefresh.rawToken());
    }

    @Transactional
    public void logoutAll(Long userId) {
        refreshTokenService.revokeAll(userId);
    }
}