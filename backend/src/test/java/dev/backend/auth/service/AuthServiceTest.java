package dev.backend.auth.service;

import dev.backend.auth.controller.dto.IssuedTokens;
import dev.backend.auth.controller.dto.SignupRequest;
import dev.backend.auth.entity.User;
import dev.backend.auth.repository.UserRepository;
import dev.backend.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Transactional
class AuthServiceIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.datasource.driver-class-name", mysql::getDriverClassName);

        // Flyway 쓰면 켜두는 게 보통 “진짜 통합”에 더 가까움
        r.add("spring.flyway.enabled", () -> true);

        // ✅ JwtService가 읽는 프로퍼티 키에 맞춰서 바꿔야 함 (예시는 흔한 형태)
        r.add("jwt.secret", () -> "test-secret-test-secret-test-secret-test-secret");
        r.add("jwt.access-ttl-seconds", () -> "3600");
        r.add("jwt.refresh-ttl-seconds", () -> "1209600");
    }

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
    }

    @Test
    void signup_throws409_whenEmailExists() {
        // given: 이미 유저가 존재
        User u = new User("test@email.com", passwordEncoder.encode("pw"));
        userRepository.save(u);

        // when
        ApiException ex = catchThrowableOfType(
                () -> authService.signup(new SignupRequest("  Test@Email.com  ", "pw")),
                ApiException.class
        );

        // then
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void signup_returnsTokens_andSavesLowercasedTrimmedEmail() {
        // when
        IssuedTokens out = authService.signup(new SignupRequest("  Test@Email.com  ", "pw"));

        // then: 토큰이 실제로 발급됨 (Mockito 없음)
        assertThat(out.accessToken()).isNotBlank();
        assertThat(out.refreshTokenRaw()).isNotBlank();
        assertThat(out.accessExpiresInSeconds()).isGreaterThan(0);

        // then: DB에 실제 저장 + 이메일 정규화 확인
        // ⚠️ findByEmail이 없다면 UserRepository에 추가하거나 다른 방식으로 조회
        Optional<User> savedOpt = userRepository.findByEmail("test@email.com");
        User saved = savedOpt.orElseThrow();

        assertThat(saved.getEmail()).isEqualTo("test@email.com");
        assertThat(saved.getPasswordHash()).isNotBlank();
        assertThat(saved.getPasswordHash()).isNotEqualTo("pw");
        assertThat(passwordEncoder.matches("pw", saved.getPasswordHash())).isTrue();
    }
}
