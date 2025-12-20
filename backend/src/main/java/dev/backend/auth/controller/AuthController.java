package dev.backend.auth.controller;

import dev.backend.auth.controller.dto.LoginRequest;
import dev.backend.auth.controller.dto.SigninRequest;
import dev.backend.auth.controller.dto.TokenPair;
import dev.backend.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signin")
    public ResponseEntity<TokenPair> signin(@RequestBody SigninRequest req) throws Exception {
        var resp = authService.signin(req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletResponse res) {
        TokenPair tokens = authService.login(req);

        setRefreshCookie(res, tokens.refreshToken());

        return ResponseEntity.ok(Map.of(
                "accessToken", tokens.accessToken(),
                "tokenType", "Bearer"
        ));
    }
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue("refresh_token") String refreshToken,
                                     HttpServletResponse res) {
        TokenPair tokens = authService.refresh(refreshToken);

        // 회전했으니 새 refresh를 쿠키로 다시 세팅
        setRefreshCookie(res, tokens.refreshToken());

        return ResponseEntity.ok(Map.of(
                "accessToken", tokens.accessToken(),
                "tokenType", "Bearer"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = "refresh_token", required = false) String refreshToken,
                                    HttpServletResponse res) {
        if (refreshToken != null) authService.logout(refreshToken);
        clearRefreshCookie(res);
        return ResponseEntity.ok().build();
    }

    private void setRefreshCookie(HttpServletResponse res, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)                 // HTTPS에서만. 로컬 테스트면 false로.
                .sameSite("Strict")           // 필요하면 Lax/None
                .path("/auth")                // refresh/logout에도 보내지게
                .maxAge(Duration.ofDays(14))
                .build();
        res.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse res) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/auth")
                .maxAge(0)
                .build();
        res.addHeader("Set-Cookie", cookie.toString());
    }
}
