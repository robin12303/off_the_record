package dev.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backend.components.WebSocketSessionRegistry;
import dev.backend.dto.AgentCommandRequest;
import dev.backend.repository.AgentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
@Testcontainers
@org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase(
        replace = org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
)
// DataJpaTest는 @Service/@Component 스캔 안 하므로 필요한 빈만 import
@Import({FrontendService.class, WebSocketSessionRegistry.class, FrontendServiceMySqlIT.TestConfig.class})
class FrontendServiceMySqlIT {

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

        // ✅ Flyway가 스키마 만들고, JPA는 검증만
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.flyway.url", mysql::getJdbcUrl);
        r.add("spring.flyway.user", mysql::getUsername);
        r.add("spring.flyway.password", mysql::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        // (선택) 필요하면 flyway clean 허용
        r.add("spring.flyway.clean-disabled", () -> "false");
    }

    @Autowired FrontendService service;
    @Autowired WebSocketSessionRegistry registry;
    @Autowired ObjectMapper om;

    @Autowired EntityManager em;
    @Autowired AgentRepository agentRepository;

    @Test
    void sendCommand_whenNoSession_returnsFalse_andDoesNotInsertLog() {
        String machineUuid = "m1";
        var cmd = AgentCommandRequest.metricsStart(machineUuid, "cmd-001");

        boolean ok = service.sendCommand(machineUuid, cmd);

        assertThat(ok).isFalse();

        em.flush();
        em.clear();

        Number cnt = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM agent_command_log WHERE command_id = :cid"
        ).setParameter("cid", "cmd-001").getSingleResult();

        assertThat(cnt.longValue()).isZero();
    }

    @Test
    void sendCommand_whenSessionOpen_sendsMessage_andUpsertsCommandLogInMySql() throws Exception {
        String machineUuid = "m1";
        String commandId = "cmd-100";
        var cmd = AgentCommandRequest.metricsStart(machineUuid, commandId);

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);

        registry.put(machineUuid, session);

        boolean ok = service.sendCommand(machineUuid, cmd);

        assertThat(ok).isTrue();

        // 1) WebSocket 메시지 전송됐는지 (payload는 JSON이라 역직렬화로 검증하는 게 안전)
        var msgCaptor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(msgCaptor.capture());

        AgentCommandRequest sent = om.readValue(msgCaptor.getValue().getPayload(), AgentCommandRequest.class);
        assertThat(sent).isEqualTo(cmd);

        // 2) DB에 로그가 진짜 들어갔는지
        em.flush();
        em.clear();

        Object[] row = (Object[]) em.createNativeQuery(
                """
                SELECT prefix, command_id, machine_uuid, task_type, status
                FROM agent_command_log
                WHERE command_id = :cid
                """
        ).setParameter("cid", commandId).getSingleResult();

        assertThat((String) row[0]).isEqualTo("METRICS");
        assertThat((String) row[1]).isEqualTo(commandId);
        assertThat((String) row[2]).isEqualTo(machineUuid);
        assertThat((String) row[3]).isEqualTo("START");
        assertThat((String) row[4]).isEqualTo("PENDING");
    }

    @Test
    void sendCommand_whenSendFails_returnsFalse_andRemovesFromRegistry_andDoesNotInsertLog() throws Exception {
        String machineUuid = "m1";
        String commandId = "cmd-200";
        var cmd = AgentCommandRequest.metricsStart(machineUuid, commandId);

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        doThrow(new IOException("boom")).when(session).sendMessage(any(TextMessage.class));

        registry.put(machineUuid, session);

        boolean ok = service.sendCommand(machineUuid, cmd);

        assertThat(ok).isFalse();
        assertThat(registry.get(machineUuid)).isNull(); // catch에서 registry.remove 호출 :contentReference[oaicite:3]{index=3}

        em.flush();
        em.clear();

        Number cnt = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM agent_command_log WHERE command_id = :cid"
        ).setParameter("cid", commandId).getSingleResult();

        assertThat(cnt.longValue()).isZero();
    }

    @Test
    void recent_readsAgentsFromMySql_orderedByLastSeenAtDesc() {
        // given: agents 테이블에 2개 심기 (repo native upsert 사용)
        LocalDateTime t1 = LocalDateTime.of(2025, 12, 25, 12, 0);
        LocalDateTime t2 = LocalDateTime.of(2025, 12, 25, 13, 0);

        agentRepository.upsertByMachineUuid("m1", "1.1.1.1", "hostA", "cpu", "gpu", "8192", "Windows", "11", t1);
        agentRepository.upsertByMachineUuid("m2", "2.2.2.2", "hostB", "cpu", "gpu", "8192", "Windows", "11", t2);

        em.flush();
        em.clear();

        // when
        var out = service.recent(); // :contentReference[oaicite:4]{index=4}

        // then
        assertThat(out).hasSize(2);

        // RecentResponse가 record로 machineUuid() 제공한다는 전형적 가정.
        // 만약 필드명이 다르면 여기만 너 프로젝트에 맞게 바꾸면 됨.
        assertThat(out.get(0).machineUuid()).isEqualTo("m2");
        assertThat(out.get(1).machineUuid()).isEqualTo("m1");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @AfterEach
    void clearRegistry() throws Exception {
        Field f = WebSocketSessionRegistry.class.getDeclaredField("byMachineUuid");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> map = (Map<String, ?>) f.get(registry);
        map.clear();
    }
}
