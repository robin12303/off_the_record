package dev.backend.auth.service;

import dev.backend.auth.entity.RefreshToken;
import dev.backend.auth.repository.RefreshTokenRepository;
import dev.backend.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private RefreshTokenRepository repo;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        service = new RefreshTokenService(repo, 1209600L); // 14일
    }

    // ---- issue ----

    @Test
    void issue_savesHashedToken_andReturnsRawTokenAndExp() {
        Instant before = Instant.now();

        RefreshTokenService.Issued issued = service.issue(7L);

        Instant after = Instant.now();

        // 1) 반환값 검증: rawToken은 비어있지 않아야 하고, exp는 지금 이후여야 함
        assertThat(issued.rawToken()).isNotBlank();
        assertThat(issued.expiresAt()).isAfter(before);

        // exp가 "대략" ttl 후인지 확인 (시계 오차 고려해서 범위로)
        Instant expectedMin = before.plusSeconds(1209600L).minusSeconds(2);
        Instant expectedMax = after.plusSeconds(1209600L).plusSeconds(2);
        assertThat(issued.expiresAt()).isBetween(expectedMin, expectedMax);

        // 2) repo.save에 들어간 RefreshToken 검증
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getExpiresAt()).isEqualTo(issued.expiresAt());

        // rawToken은 저장되면 안 되고(서버는 해시만 저장)
        // 저장되는 tokenHash는 rawToken과 다르고 64 hex이어야 정상(SHA-256 hex)
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(saved.getTokenHash()).isNotEqualTo(issued.rawToken());
        assertThat(saved.getTokenHash()).matches("^[0-9a-f]{64}$");
    }

    // ---- validateAndRotate ----

    @Test
    void validateAndRotate_returnsUserId_andRevokes_whenValid() {
        String raw = "raw-refresh-token";

        RefreshToken rt = mock(RefreshToken.class);
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(rt));
        when(rt.isRevoked()).thenReturn(false);
        when(rt.isExpired()).thenReturn(false);
        when(rt.getUserId()).thenReturn(7L);

        Long userId = service.validateAndRotate(raw);

        assertThat(userId).isEqualTo(7L);
        verify(rt).revokeNow();
    }

    @Test
    void validateAndRotate_throws401_whenNotFound() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(
                () -> service.validateAndRotate("raw"),
                ApiException.class
        );

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getCode()).isEqualTo("INVALID_REFRESH");
        verify(repo).findByTokenHash(anyString());
    }

    @Test
    void validateAndRotate_throws401_whenRevoked() {
        // raw는 아무거나, repo는 anyString()으로 받으니까 상관없음
        String raw = "raw";

        RefreshToken rt = new RefreshToken(
                7L,
                "anyhash",
                Instant.now().plusSeconds(60)
        );
        rt.setRevokedAt(Instant.now()); // ✅ revoked 상태 만들기

        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(rt));

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.validateAndRotate(raw)
        );

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getCode()).isEqualTo("INVALID_REFRESH");
    }

    @Test
    void validateAndRotate_throws401_whenExpired() {
        RefreshToken rt = mock(RefreshToken.class);
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(rt));
        when(rt.isRevoked()).thenReturn(false);
        when(rt.isExpired()).thenReturn(true);

        ApiException ex = catchThrowableOfType(() -> service.validateAndRotate("raw"), ApiException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getCode()).isEqualTo("INVALID_REFRESH");
        verify(rt, never()).revokeNow();
    }

    // ---- revoke ----

    @Test
    void revoke_revokesIfPresent() {
        RefreshToken rt = mock(RefreshToken.class);
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(rt));

        service.revoke("raw");

        verify(rt).revokeNow();
    }

    @Test
    void revoke_doesNothingIfNotPresent() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.empty());

        service.revoke("raw");

        verifyNoMoreInteractions(repo);
    }

    // ---- revokeAll ----

    @Test
    void revokeAll_callsRepoWithNow() {
        service.revokeAll(7L);

        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repo).revokeAllByUserId(eq(7L), nowCaptor.capture());

        // "지금" 근처여야 함
        assertThat(Duration.between(nowCaptor.getValue(), Instant.now()).abs())
                .isLessThan(Duration.ofSeconds(2));
    }

    // ---- validate ----

    @Test
    void validate_returnsUserId_whenValid() {
        RefreshToken rt = mock(RefreshToken.class);
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(rt));
        when(rt.getRevokedAt()).thenReturn(null);
        when(rt.getExpiresAt()).thenReturn(Instant.now().plusSeconds(60));
        when(rt.getUserId()).thenReturn(7L);

        Long userId = service.validate("raw");

        assertThat(userId).isEqualTo(7L);
    }

    @Test
    void validate_throwsREVOKED_whenRevokedAtNotNull_realEntity() {
        RefreshToken rt = new RefreshToken(
                7L, "anyhash", Instant.now().plusSeconds(60)
        );
        rt.setRevokedAt(Instant.now());

        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(rt));

        ApiException ex = assertThrows(ApiException.class, () -> service.validate("raw"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getCode()).isEqualTo("REVOKED_REFRESH");
    }

    @Test
    void validate_throwsEXPIRED_whenExpiresAtPast() {
        RefreshToken rt = mock(RefreshToken.class);
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(rt));
        when(rt.getRevokedAt()).thenReturn(null);
        when(rt.getExpiresAt()).thenReturn(Instant.now().minusSeconds(1));

        ApiException ex = catchThrowableOfType(() -> service.validate("raw"), ApiException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getCode()).isEqualTo("EXPIRED_REFRESH");
    }
}
