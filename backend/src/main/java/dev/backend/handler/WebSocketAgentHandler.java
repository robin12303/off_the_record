package dev.backend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import dev.backend.components.WebSocketSessionRegistry;
import dev.backend.dto.ReceivedMessage;
import dev.backend.service.AgentEventHandlerService;
import dev.backend.service.AgentHandlerService;
import dev.backend.sse.controller.dto.MetricEventData;
import dev.backend.sse.service.SsePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
@Component
public class WebSocketAgentHandler extends TextWebSocketHandler {

    private static final String ATTR_MACHINE_UUID = "machineUuid";

    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final AgentHandlerService agentHandlerService;
    private final AgentEventHandlerService agentEventHandlerService;
    private final SsePushService ssePushService;

    // machineUuid 별 limiter
    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WS connected: id={}, uri={}, remote={}",
                session.getId(),
                session.getUri(),
                session.getRemoteAddress());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String machineUuid = (String) session.getAttributes().get(ATTR_MACHINE_UUID);
        if (machineUuid != null) {
            registry.remove(machineUuid, session);
            limiters.remove(machineUuid); // 메모리 정리
        }
        log.info("Closed {} status={}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();

        try {
            ReceivedMessage received = objectMapper.readValue(payload, ReceivedMessage.class);

            String prefix = received.prefix();
            if (prefix == null) {
                log.info("Unrecognized prefix: null");
                return;
            }

            switch (prefix) {
                case "HEARTBEAT" -> handleHeartbeat(session, received);

                case "METRICS" -> handleMetrics(session, received);

                case "EVENT" -> handleEvent(session, received);

                default -> log.info("Unrecognized prefix: {}", prefix);
            }
        } catch (Exception e) {
            // 운영에서 payload 전체 로그는 위험/폭탄일 수 있음. 필요하면 길이 제한 걸어라.
            log.error("Error parsing message: {}", payload, e);
        }
    }

    private void handleHeartbeat(WebSocketSession session, ReceivedMessage received) throws Exception {
        String incomingUuid = received.machineUuid();
        if (incomingUuid == null || incomingUuid.isBlank()) {
            log.warn("HEARTBEAT missing machineUuid: session={}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String boundUuid = (String) session.getAttributes().get(ATTR_MACHINE_UUID);

        // ✅ 최초 1회만 바인딩
        if (boundUuid == null) {
            session.getAttributes().put(ATTR_MACHINE_UUID, incomingUuid);
            registry.put(incomingUuid, session);
            boundUuid = incomingUuid;

            log.info("Bound machineUuid to session: sessionId={}, machineUuid={}", session.getId(), boundUuid);
        } else if (!boundUuid.equals(incomingUuid)) {
            // ✅ 세션 중간에 uuid 바꾸려는 시도 차단
            log.warn("machineUuid mismatch (possible spoofing): sessionId={}, bound={}, incoming={}",
                    session.getId(), boundUuid, incomingUuid);
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // ✅ heartbeat은 계속 처리
        agentHandlerService.HeartBeat(session, received);
    }

    private void handleMetrics(WebSocketSession session, ReceivedMessage received) throws Exception {
        String guid = requireBoundUuid(session, received);
        if (guid == null) return;

        String taskType = received.taskType();
        if (taskType == null) {
            log.info("[METRICS]: Unrecognized taskType: null (machineUuid={})", guid);
            return;
        }

        switch (taskType) {
            case "START", "STOP" -> agentHandlerService.MetricsCommand(session, received);
            default -> log.info("[METRICS]: Unrecognized taskType: {} (machineUuid={})", taskType, guid);
        }
    }

    private void handleEvent(WebSocketSession session, ReceivedMessage received) throws Exception {
        String guid = requireBoundUuid(session, received);
        if (guid == null) return;

        // taskType == METRIC 이벤트만 처리한다고 했으니 그대로
        if (!"METRIC".equals(received.taskType())) {
            return;
        }

        // Guava RateLimiter.create(x) = permits per SECOND
        RateLimiter limiter = limiters.computeIfAbsent(guid, g -> RateLimiter.create(60.0));

        // ✅ 먼저 드롭
        if (!limiter.tryAcquire()) {
            return;
        }

        MetricEventData data = objectMapper.readValue(received.payload(), MetricEventData.class);
        agentEventHandlerService.MetricEvent(received, data);
        ssePushService.broadcastMetricsToMachine(guid, "metric_event", data);
    }

    /**
     * 세션에 바인딩된 machineUuid 기준으로만 처리 (received.machineUuid는 검증용)
     */
    private String requireBoundUuid(WebSocketSession session, ReceivedMessage received) throws Exception {
        String boundUuid = (String) session.getAttributes().get(ATTR_MACHINE_UUID);
        String incomingUuid = received.machineUuid();

        if (boundUuid == null) {
            log.warn("Message before HEARTBEAT binding: sessionId={}, prefix={}", session.getId(), received.prefix());
            session.close(CloseStatus.POLICY_VIOLATION);
            return null;
        }

        // 혹시 클라가 계속 machineUuid를 보내는 구조라면, mismatch는 차단
        if (incomingUuid != null && !boundUuid.equals(incomingUuid)) {
            log.warn("machineUuid mismatch (possible spoofing): sessionId={}, bound={}, incoming={}",
                    session.getId(), boundUuid, incomingUuid);
            session.close(CloseStatus.POLICY_VIOLATION);
            return null;
        }

        return boundUuid;
    }
}
