package dev.backend.auth.repository;

import dev.backend.auth.entity.RefreshToken;
import dev.backend.auth.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryMySqlTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);

        // Flyway 쓰면 보통 validate 권장 (Flyway가 스키마 만들고 JPA는 검증만)
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired RefreshTokenRepository repo;
    @Autowired EntityManager em;

    private Long createUserId() {
        User u = new User("t@t.com", "pw-hash");
        em.persist(u);
        em.flush();          // IDENTITY면 flush해야 id 생김
        return u.getId();
    }

    @Test
    void revokeAllByUserId_works_onMySql() {
        Long userId = createUserId();

        Instant exp = Instant.parse("2030-01-01T00:00:00Z");
        repo.save(new RefreshToken(userId, "h1", exp));
        repo.save(new RefreshToken(userId, "h2", exp));

        em.flush();
        em.clear();

        Instant now = Instant.parse("2025-12-25T00:00:00Z");
        int updated = repo.revokeAllByUserId(userId, now);

        assertThat(updated).isEqualTo(2);
    }
}
