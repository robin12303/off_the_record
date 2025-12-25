package dev.backend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import dev.backend.components.WebSocketSessionRegistry;
import dev.backend.dto.ReceivedMessage;
import dev.backend.service.AgentEventHandlerService;
import dev.backend.service.AgentHandlerService;
import dev.backend.sse.controller.dto.MetricEventData;
import dev.backend.sse.service.SsePushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAgentHandlerTest {

    private WebSocketSessionRegistry registry;
    private ObjectMapper objectMapper;
    private AgentHandlerService agentHandlerService;
    private AgentEventHandlerService agentEventHandlerService;
    private SsePushService ssePushService;

    private WebSocketAgentHandler handler;

    @BeforeEach
    void setUp() {
        registry = mock(WebSocketSessionRegistry.class);
        objectMapper = mock(ObjectMapper.class);
        agentHandlerService = mock(AgentHandlerService.class);
        agentEventHandlerService = mock(AgentEventHandlerService.class);
        ssePushService = mock(SsePushService.class);

        handler = new WebSocketAgentHandler(
                registry,
                objectMapper,
                agentHandlerService,
                agentEventHandlerService,
                ssePushService
        );
    }

    @Test
    void heartbeat_firstTime_bindsUuid_registersSession_callsService() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("s1");

        ReceivedMessage received = mock(ReceivedMessage.class);
        when(received.prefix()).thenReturn("HEARTBEAT");
        when(received.machineUuid()).thenReturn("m1");

        when(objectMapper.readValue(anyString(), eq(ReceivedMessage.class))).thenReturn(received);

        handler.handleTextMessage(session, new TextMessage("{\"dummy\":true}"));

        assertEquals("m1", attrs.get("machineUuid"));
        verify(registry).put("m1", session);
        verify(agentHandlerService).HeartBeat(session, received);
        verify(session, never()).close(any());
    }

    @Test
    void heartbeat_missingUuid_closesSession_1008() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        //when(session.getAttributes()).thenReturn(attrs);
        //when(session.getId()).thenReturn("s1");

        ReceivedMessage received = mock(ReceivedMessage.class);
        when(received.prefix()).thenReturn("HEARTBEAT");
        when(received.machineUuid()).thenReturn("   "); // blank

        when(objectMapper.readValue(anyString(), eq(ReceivedMessage.class))).thenReturn(received);

        handler.handleTextMessage(session, new TextMessage("{}"));

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(registry);
        verifyNoInteractions(agentHandlerService);
    }

    @Test
    void heartbeat_uuidMismatch_afterBinding_closesSession_1008() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        attrs.put("machineUuid", "m1");
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("s1");

        ReceivedMessage received = mock(ReceivedMessage.class);
        when(received.prefix()).thenReturn("HEARTBEAT");
        when(received.machineUuid()).thenReturn("m2");

        when(objectMapper.readValue(anyString(), eq(ReceivedMessage.class))).thenReturn(received);

        handler.handleTextMessage(session, new TextMessage("{}"));

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(agentHandlerService, never()).HeartBeat(any(), any());
        verify(registry, never()).put(anyString(), any());
    }

    @Test
    void metrics_start_callsMetricsCommand_whenBound() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        attrs.put("machineUuid", "m1");
        when(session.getAttributes()).thenReturn(attrs);
        //when(session.getId()).thenReturn("s1");

        ReceivedMessage received = mock(ReceivedMessage.class);
        when(received.prefix()).thenReturn("METRICS");
        when(received.taskType()).thenReturn("START");
        when(received.machineUuid()).thenReturn("m1");

        when(objectMapper.readValue(anyString(), eq(ReceivedMessage.class))).thenReturn(received);

        handler.handleTextMessage(session, new TextMessage("{}"));

        verify(agentHandlerService).MetricsCommand(session, received);
        verify(session, never()).close(any());
    }

    @Test
    void event_metric_callsMetricEventAndBroadcast_whenLimiterAllows() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        attrs.put("machineUuid", "m1");
        when(session.getAttributes()).thenReturn(attrs);
        //when(session.getId()).thenReturn("s1");

        ReceivedMessage received = mock(ReceivedMessage.class);
        when(received.prefix()).thenReturn("EVENT");
        when(received.taskType()).thenReturn("METRIC");
        when(received.machineUuid()).thenReturn("m1");
        when(received.payload()).thenReturn("{\"x\":1}");

        // ✅ 이게 안 맞으면 아래 흐름 자체가 안 탐
        when(objectMapper.readValue(anyString(), eq(ReceivedMessage.class))).thenReturn(received);

        // ✅ mock limiter를 실제 map에 박아 넣기
        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.tryAcquire()).thenReturn(true);
        putLimiter(handler, "m1", limiter);

        MetricEventData data = mock(MetricEventData.class);
        when(objectMapper.readValue(eq("{\"x\":1}"), eq(MetricEventData.class))).thenReturn(data);

        handler.handleTextMessage(session, new TextMessage("{\"whatever\":true}"));

        // ✅ 이 verify들이 통과하면, 위 stub들이 "사용"된 거라 UnnecessaryStubbing이 안 남
        verify(limiter).tryAcquire();
        verify(objectMapper).readValue(eq("{\"x\":1}"), eq(MetricEventData.class));
        verify(agentEventHandlerService).MetricEvent(received, data);
        verify(ssePushService).broadcastMetricsToMachine("m1", "metric_event", data);
        verify(session, never()).close(any());
    }

    @Test
    void event_metric_drops_whenLimiterDenies() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        attrs.put("machineUuid", "m1");
        when(session.getAttributes()).thenReturn(attrs);
        //when(session.getId()).thenReturn("s1");

        ReceivedMessage received = mock(ReceivedMessage.class);
        when(received.prefix()).thenReturn("EVENT");
        when(received.taskType()).thenReturn("METRIC");
        when(received.machineUuid()).thenReturn("m1");
       // when(received.payload()).thenReturn("{\"x\":1}");

        when(objectMapper.readValue(anyString(), eq(ReceivedMessage.class))).thenReturn(received);

        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.tryAcquire()).thenReturn(false);
        putLimiter(handler, "m1", limiter);

        handler.handleTextMessage(session, new TextMessage("{\"dummy\":true}"));

        // limiter가 false면 payload 파싱도 안 하고 그냥 반환해야 정상
        verify(objectMapper, never()).readValue(eq("{\"x\":1}"), eq(MetricEventData.class));
        verifyNoInteractions(agentEventHandlerService);
        verifyNoInteractions(ssePushService);
    }

    @Test
    void afterConnectionClosed_removesRegistryAndLimiter() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        attrs.put("machineUuid", "m1");
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("s1");

        // limiter 하나 심어두고 닫을 때 제거되는지 확인
        putLimiter(handler, "m1", mock(RateLimiter.class));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(registry).remove("m1", session);
        assertFalse(getLimiters(handler).containsKey("m1"));
    }

    // ----------------- reflection helpers -----------------

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, RateLimiter> getLimiters(WebSocketAgentHandler handler) throws Exception {
        Field f = WebSocketAgentHandler.class.getDeclaredField("limiters");
        f.setAccessible(true);
        return (ConcurrentHashMap<String, RateLimiter>) f.get(handler);
    }

    private static void putLimiter(WebSocketAgentHandler handler, String uuid, RateLimiter limiter) throws Exception {
        getLimiters(handler).put(uuid, limiter);
    }


}
