package dev.backend.auth;
import dev.backend.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    // ⚠️ HMAC 키는 너무 짧으면 예외 날 수 있음. 충분히 길게.
    private static final String SECRET =
            "test-secret-test-secret-test-secret-test-secret-test-secret";

    @Test
    void issue_then_parse_ok() {
        JwtService jwt = new JwtService(SECRET, 900);

        String token = jwt.issueAccessToken(123L, "test@test.com");
        assertNotNull(token);

        Claims claims = jwt.parse(token);

        assertEquals("test@test.com", claims.getSubject());
        assertEquals(123, claims.get("uid", Integer.class)); // Long로 안 나올 수 있어서 int로 확인
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(new Date()));
    }

    @Test
    void tampered_token_should_throw() {
        JwtService jwt = new JwtService(SECRET, 900);

        String token = jwt.issueAccessToken(1L, "test@test.com");
        String tampered = token.substring(0, token.length() - 1) + "x";

        assertThrows(JwtException.class, () -> jwt.parse(tampered));
    }

    @Test
    void wrong_secret_should_throw() {
        JwtService jwt1 = new JwtService(SECRET, 900);
        JwtService jwt2 = new JwtService("another-secret-another-secret-another-secret-another-secret", 900);

        String token = jwt1.issueAccessToken(1L, "test@test.com");

        assertThrows(JwtException.class, () -> jwt2.parse(token));
    }

    @Test
    void expired_token_should_throw() throws InterruptedException {
        JwtService shortLived = new JwtService(SECRET, 1); // 1초 TTL
        String token = shortLived.issueAccessToken(1L, "test@test.com");

        Thread.sleep(1200);

        assertThrows(ExpiredJwtException.class, () -> shortLived.parse(token));
    }

    @Test
    void ttl_is_applied() {
        JwtService jwt = new JwtService(SECRET, 7);
        assertEquals(7, jwt.getAccessTtlSeconds());
    }
}
