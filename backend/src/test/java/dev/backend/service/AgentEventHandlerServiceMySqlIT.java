package dev.backend.service;

import dev.backend.dto.ReceivedMessage;
import dev.backend.sse.controller.dto.MetricEventData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

// Boot 4 패키지
@org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
@Testcontainers
@org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase(
        replace = org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
)
// ✅ DataJpaTest는 @Service를 스캔 안 하니까 직접 import
@Import(AgentEventHandlerService.class)
class AgentEventHandlerServiceMySqlIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);

        // Flyway도 같은 DB를 보게
        r.add("spring.flyway.enabled", () -> true);
        r.add("spring.flyway.url", mysql::getJdbcUrl);
        r.add("spring.flyway.user", mysql::getUsername);
        r.add("spring.flyway.password", mysql::getPassword);

        // 스키마는 Flyway가 만들고, JPA는 검증만
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired AgentEventHandlerService service;
    @Autowired EntityManager em;

    @Test
    void metricEvent_insertsRow_intoMySql() {
        // given
        ReceivedMessage received = new ReceivedMessage(
                "EVENT",
                null,
                "m1",
                null,
                "METRIC",
                null
        );

        MetricEventData data = new MetricEventData(
                "2025-12-25T13:00:00",
                1000,
                12345L,
                99L
        );

        // when
        service.MetricEvent(received, data);

        // then: native insert라 1차 캐시 믿지 말고 flush/clear
        em.flush();
        em.clear();

        Number cnt = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM agent_metrics WHERE machine_uuid = :uuid"
        ).setParameter("uuid", "m1").getSingleResult();

        assertThat(cnt.longValue()).isEqualTo(1L);

        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT window_ms, window_end_ms, keystrokes " +
                        "FROM agent_metrics WHERE machine_uuid = :uuid LIMIT 1"
        ).setParameter("uuid", "m1").getSingleResult();

        assertThat(((Number) row[0]).intValue()).isEqualTo(1000);
        assertThat(((Number) row[1]).longValue()).isEqualTo(12345L);
        assertThat(((Number) row[2]).longValue()).isEqualTo(99L);
    }
}
