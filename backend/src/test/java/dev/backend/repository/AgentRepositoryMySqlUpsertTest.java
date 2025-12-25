package dev.backend.repository;

import dev.backend.entity.Agent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Duration;
import java.time.LocalDateTime;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
@Testcontainers
@org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase(
        replace = org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
)
class AgentRepositoryMySqlUpsertTest {

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

        // ✅ Flyway도 같은 DB를 보게
        r.add("spring.flyway.url", mysql::getJdbcUrl);
        r.add("spring.flyway.user", mysql::getUsername);
        r.add("spring.flyway.password", mysql::getPassword);

        // ✅ Flyway가 스키마를 만들게 하고, JPA는 검증만
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        // (선택) 테스트마다 깨끗하게 시작하고 싶으면
        r.add("spring.flyway.clean-disabled", () -> "false");
    }

    @Autowired
    AgentRepository agentRepository;
    @Autowired
    EntityManager em;

    @Test
    void upsert_inserts_then_updates() {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(5);
        agentRepository.upsertByMachineUuid(
                "m1", "1.1.1.1", "hostA", "cpuA", "gpuA",
                "8192", "Windows", "11", t1
        );

        em.clear(); // 1차 캐시 비우고 DB에서 다시 읽게
        Agent a1 = agentRepository.findByMachineUuid("m1").orElseThrow();
        assertThat(a1.getIpAddress()).isEqualTo("1.1.1.1");
        assertThat(a1.getHostName()).isEqualTo("hostA");

        LocalDateTime t2 = LocalDateTime.now();
        agentRepository.upsertByMachineUuid(
                "m1", "2.2.2.2", "hostB", "cpuB", "gpuB",
                "16384", "Windows", "11", t2
        );

        em.clear();
        Agent a2 = agentRepository.findByMachineUuid("m1").orElseThrow();
        assertThat(a2.getIpAddress()).isEqualTo("2.2.2.2");
        assertThat(a2.getHostName()).isEqualTo("hostB");
        assertThat(a2.getCpuName()).isEqualTo("cpuB");
        assertThat(Duration.between(t2, a2.getLastSeenAt()).abs())
                .isLessThan(Duration.ofSeconds(1));
    }
}