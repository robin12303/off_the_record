package dev.backend.auth.controller;

import dev.backend.auth.controller.dto.IssuedTokens;
import dev.backend.auth.controller.dto.TokenResponse;
import dev.backend.auth.service.AuthService;
import dev.backend.auth.service.RefreshTokenService;
import dev.backend.auth.controller.dto.LoginRequest;
import dev.backend.auth.controller.dto.SignupRequest;
import dev.backend.auth.entity.User;
import dev.backend.exception.ApiException;
import dev.backend.auth.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TokenResponse> signup(@Valid @RequestBody SignupRequest req) {
        IssuedTokens tokens = authService.signup(req);

        ResponseCookie cookie = ResponseCookie.from("refresh_token", tokens.refreshTokenRaw())
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofDays(14))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new TokenResponse(tokens.accessToken(), "Bearer", tokens.accessExpiresInSeconds()));


    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        IssuedTokens tokens = authService.login(req);

        ResponseCookie cookie = ResponseCookie.from("refresh_token", tokens.refreshTokenRaw())
                .httpOnly(true)
                .secure(false)              // 로컬=false, 운영(HTTPS)=true
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofDays(14))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new TokenResponse(tokens.accessToken(), "Bearer", tokens.accessExpiresInSeconds()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name="refresh_token", required=false) String refreshToken
    ) {
        // refresh 토큰이 없으면 재발급 불가
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MISSING_REFRESH", "Refresh token missing");
        }

        IssuedTokens tokens = authService.refresh(refreshToken);

        ResponseCookie cookie = ResponseCookie.from("refresh_token", tokens.refreshTokenRaw())
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofDays(14))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new TokenResponse(tokens.accessToken(), "Bearer", tokens.accessExpiresInSeconds()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logoutAllDevices(
            Authentication authentication,
            @CookieValue(name = "refresh_token", required = false) String refreshToken
    ) {
        // ✅ 1) 현재 사용자 식별 (JwtAuthFilter에서 principal=email로 넣었을 때)
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Not authenticated");
        }

        String email = (String) authentication.getPrincipal();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));

        // ✅ 2) 모든 refresh 토큰 폐기 (모든 기기 로그아웃)
        authService.logoutAll(user.getId());

        // ✅ 3) 현재 브라우저 쿠키도 삭제 (이 기기 포함)
        ResponseCookie clear = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(false)     // 로컬 false, 운영 HTTPS true
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clear.toString())
                .build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAllDevices(
            @CookieValue(name="refresh_token", required=false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MISSING_REFRESH", "Refresh token missing");
        }

        Long userId = refreshTokenService.validate(refreshToken); // ✅ rotate 말고 "검증만"
        authService.logoutAll(userId);

        ResponseCookie clear = ResponseCookie.from("refresh_token", "")
                .httpOnly(true).secure(false).sameSite("Lax")
                .path("/api/auth").maxAge(0).build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clear.toString())
                .build();
    }


}