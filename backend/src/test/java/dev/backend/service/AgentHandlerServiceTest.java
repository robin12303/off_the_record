package dev.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backend.components.WebSocketSessionRegistry;
import dev.backend.dto.HeartBeat;
import dev.backend.dto.ReceivedMessage;
import dev.backend.repository.AgentCommandLogRepository;
import dev.backend.repository.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentHandlerServiceTest {

    @Mock AgentRepository agentRepository;
    @Mock AgentCommandLogRepository agentCommandLogRepository;
    @Mock WebSocketSessionRegistry registry;
    @Mock ObjectMapper objectMapper;

    AgentHandlerService service;

    @BeforeEach
    void setUp() {
        service = new AgentHandlerService(agentRepository, agentCommandLogRepository, registry, objectMapper);
    }

    // ---------------- HEARTBEAT ----------------

    @Test
    void heartBeat_firstTime_bindsUuid_registersSession_andUpserts() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("s1");
        when(session.getRemoteAddress()).thenReturn(new InetSocketAddress("1.2.3.4", 12345));

        String payload = "{\"hb\":true}";
        ReceivedMessage received = new ReceivedMessage(
                "HEARTBEAT",
                null,
                "m1",
                null,
                null,
                payload
        );

        HeartBeat hb = new HeartBeat(
                "m1",
                "hostA",
                "cpuA",
                "gpuA",
                "8192",
                "Windows",
                "11"
        );

        when(objectMapper.readValue(eq(payload), eq(HeartBeat.class))).thenReturn(hb);

        service.HeartBeat(session, received);

        // 최초 바인딩 + registry 등록
        assertThat(attrs.get(AgentHandlerService.ATTR_MACHINE_UUID)).isEqualTo("m1");
        verify(registry).put("m1", session);

        // DB upsert 호출 (시간은 매번 달라지니 any(LocalDateTime.class))
        verify(agentRepository).upsertByMachineUuid(
                eq("m1"),
                eq("1.2.3.4"),
                eq("hostA"),
                eq("cpuA"),
                eq("gpuA"),
                eq("8192"),
                eq("Windows"),
                eq("11"),
                any(LocalDateTime.class)
        );

        verify(session, never()).close(any());
    }

    @Test
    void heartBeat_missingIncomingUuid_kicks_1008_andDoesNotTouchDbOrRegistry() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");

        ReceivedMessage received = new ReceivedMessage(
                "HEARTBEAT",
                null,
                "   ", // blank
                null,
                null,
                "{\"hb\":true}"
        );

        service.HeartBeat(session, received);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(registry);
        verifyNoInteractions(agentRepository);
    }

    @Test
    void heartBeat_incomingUuidMismatch_afterBinding_kicks_andRemovesRegistry() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        attrs.put(AgentHandlerService.ATTR_MACHINE_UUID, "m1");
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("s1");

        // 이미 바인딩된 uuid와 다른 uuid가 들어오면 바로 킥
        ReceivedMessage received = new ReceivedMessage(
                "HEARTBEAT",
                null,
                "m2",
                null,
                null,
                "{\"hb\":true}"
        );

        service.HeartBeat(session, received);

        verify(registry).remove("m1", session);
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(agentRepository);
    }

    @Test
    void heartBeat_payloadUuidMismatch_kicks_andDoesNotUpsert() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("s1");

        String payload = "{\"hb\":true}";
        ReceivedMessage received = new ReceivedMessage(
                "HEARTBEAT",
                null,
                "m1",
                null,
                null,
                payload
        );

        // payload의 uuid가 boundUuid와 다르면 킥
        HeartBeat hb = new HeartBeat(
                "m2", // mismatch
                "hostA",
                "cpuA",
                "gpuA",
                "8192",
                "Windows",
                "11"
        );

        when(objectMapper.readValue(eq(payload), eq(HeartBeat.class))).thenReturn(hb);

        service.HeartBeat(session, received);

        // 최초 바인딩은 일어나고 put도 됨
        verify(registry).put("m1", session);
        // mismatch라 바로 remove + close
        verify(registry).remove("m1", session);
        verify(session).close(CloseStatus.POLICY_VIOLATION);

        verifyNoInteractions(agentRepository);
    }

    @Test
    void heartBeat_payloadParseFails_shouldNotKick_andNotUpsert_butBindingStillHappens() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("s1");

        String payload = "{\"bad\":true}";
        ReceivedMessage received = new ReceivedMessage(
                "HEARTBEAT",
                null,
                "m1",
                null,
                null,
                payload
        );

        when(objectMapper.readValue(eq(payload), eq(HeartBeat.class)))
                .thenThrow(new RuntimeException("parse fail"));

        service.HeartBeat(session, received);

        // 현재 코드 기준: 파싱 실패는 warn만 찍고 킥 안 함
        verify(registry).put("m1", session);
        verify(session, never()).close(any());
        verifyNoInteractions(agentRepository);
    }

    // ---------------- METRICS COMMAND ----------------

    @Test
    void metricsCommand_beforeBinding_kicks_1008() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("s1");

        ReceivedMessage received = new ReceivedMessage(
                "METRICS",
                "cmd-1",
                "m1",
                null,
                "START",
                null
        );

        service.MetricsCommand(session, received);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(agentCommandLogRepository);
    }

    @Test
    void metricsCommand_start_upsertsAccepted_usingBoundUuid() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        attrs.put(AgentHandlerService.ATTR_MACHINE_UUID, "m1");
        when(session.getAttributes()).thenReturn(attrs);

        ReceivedMessage received = new ReceivedMessage(
                "METRICS",
                "cmd-1",
                "m1",
                null,
                "START",
                null
        );

        service.MetricsCommand(session, received);

        verify(agentCommandLogRepository).upsertByCommandId(
                "METRICS", "cmd-1", "m1", "START", "ACCEPTED"
        );
        verifyNoMoreInteractions(agentCommandLogRepository);
    }

    @Test
    void metricsCommand_stop_upsertsAccepted_usingBoundUuid() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        attrs.put(AgentHandlerService.ATTR_MACHINE_UUID, "m1");
        when(session.getAttributes()).thenReturn(attrs);

        ReceivedMessage received = new ReceivedMessage(
                "METRICS",
                "cmd-2",
                "m1",
                null,
                "STOP",
                null
        );

        service.MetricsCommand(session, received);

        verify(agentCommandLogRepository).upsertByCommandId(
                "METRICS", "cmd-2", "m1", "STOP", "ACCEPTED"
        );
        verifyNoMoreInteractions(agentCommandLogRepository);
    }

    @Test
    void metricsCommand_taskTypeNull_shouldDoNothing() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        attrs.put(AgentHandlerService.ATTR_MACHINE_UUID, "m1");
        when(session.getAttributes()).thenReturn(attrs);

        ReceivedMessage received = new ReceivedMessage(
                "METRICS",
                "cmd-3",
                "m1",
                null,
                null, // taskType null
                null
        );

        service.MetricsCommand(session, received);

        verifyNoInteractions(agentCommandLogRepository);
    }
}
