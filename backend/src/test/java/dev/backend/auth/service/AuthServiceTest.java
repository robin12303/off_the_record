package dev.backend.auth.service;
import dev.backend.auth.controller.dto.IssuedTokens;
import dev.backend.auth.controller.dto.LoginRequest;
import dev.backend.auth.controller.dto.SignupRequest;
import dev.backend.auth.entity.User;
import dev.backend.auth.repository.UserRepository;
import dev.backend.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokenService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);
    }

    // ---------- signup ----------
    @Test
    void signup_throws409_whenEmailExists() {
        SignupRequest req = new SignupRequest("Test@Email.com", "pw");
        when(userRepository.existsByEmail("test@email.com")).thenReturn(true);

        ApiException ex = catchThrowableOfType(() -> authService.signup(req), ApiException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo("EMAIL_ALREADY_EXISTS");

        verify(userRepository).existsByEmail("test@email.com");
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(passwordEncoder, jwtService, refreshTokenService);
    }

    @Test
    void signup_returnsTokens_andSavesLowercasedTrimmedEmail() {
        SignupRequest req = new SignupRequest("  Test@Email.com  ", "pw");

        when(userRepository.existsByEmail("test@email.com")).thenReturn(false);
        when(passwordEncoder.encode("pw")).thenReturn("hash");

        // save() 결과로 id가 있는 User를 반환해야 access 발급이 가능
        User saved = new User("test@email.com", "hash");
        saved.setId(10L); // <- 아래 주석 참고
        when(userRepository.save(any(User.class))).thenReturn(saved);

        when(jwtService.issueAccessToken(10L, "test@email.com")).thenReturn("access.jwt");
        when(jwtService.getAccessTtlSeconds()).thenReturn(3600L);

        // RefreshTokenService.issue()가 반환하는 타입에 맞춰서 수정
        when(refreshTokenService.issue(10L)).thenReturn(new RefreshTokenService.Issued("raw.refresh", null));

        IssuedTokens out = authService.signup(req);

        assertThat(out.accessToken()).isEqualTo("access.jwt");
        assertThat(out.accessExpiresInSeconds()).isEqualTo(3600L);
        assertThat(out.refreshTokenRaw()).isEqualTo("raw.refresh");

        // 저장된 유저 이메일이 정규화 되었는지 확인
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("test@email.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash");
    }
}
