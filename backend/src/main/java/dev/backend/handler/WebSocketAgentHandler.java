package dev.backend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backend.components.WebSocketSessionRegistry;
import dev.backend.service.AgentEventHandlerService;
import dev.backend.sse.controller.dto.KeyEventData;
import dev.backend.sse.controller.dto.MetricEventData;
import dev.backend.dto.ReceivedMessage;
import dev.backend.service.AgentPushService;
import dev.backend.service.AgentHandlerService;
import dev.backend.sse.service.SsePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.common.util.concurrent.RateLimiter;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
@Component
public class WebSocketAgentHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final AgentHandlerService agentHandlerService;
    private final AgentEventHandlerService agentEventHandlerService;
    private final SsePushService ssePushService;

    // machineGuid별 limiter
    private final ConcurrentHashMap<String, RateLimiter> keyLimiters = new ConcurrentHashMap<>();
    private final AgentPushService agentPushService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WS connected: id={}, uri={}, remote={}",
                session.getId(),
                session.getUri(),
                session.getRemoteAddress());

    }
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String machineUuid = (String) session.getAttributes().get("machineUuid");
        if (machineUuid != null) {
            registry.remove(machineUuid, session);
            keyLimiters.remove(machineUuid); // ✅ 메모리 정리
        }
        log.info("Closed {} status={}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            ReceivedMessage received = objectMapper.readValue(payload, ReceivedMessage.class);

            switch (received.prefix()) {
                case "READ" -> {
                    switch (received.taskType()){
                        case "START" -> {
                            agentHandlerService.handleStartRead(session, received);
                        }
                        case "STOP" -> {
                            agentHandlerService.handleStopRead(session, received);
                        }
                        default -> log.info("[READ]: Unrecognized taskType: {}", received.taskType());
                    }
                }

                case "HEARTBEAT" -> {
                    session.getAttributes().put("machineUuid", received.machineUuid());
                    registry.put(received.machineUuid(), session);
                    agentHandlerService.handleHeartBeat(session, received);

                }

                case "METRICS" -> {
                    switch (received.taskType()){
                        case "START" -> {
                            agentHandlerService.handleStartMetrics(session, received);
                        }
                        case "STOP" -> {
                            agentHandlerService.handleStopMetrics(session, received);
                        }
                        default -> log.info("[METRICS]: Unrecognized taskType: {}", received.taskType());
                    }
                }

                case "EVENT" -> {

                    String guid = received.machineUuid();

                    if ("KEY".equals(received.taskType())) {
                        // 예: 초당 60개 허용 (너무 빡세면 30~120 사이로 조절)
                        RateLimiter limiter = keyLimiters.computeIfAbsent(guid, g -> RateLimiter.create(60.0));

                        // ✅ 제한 초과면 드롭
                        if (!limiter.tryAcquire()) {
                            return;
                        }

                        KeyEventData data = objectMapper.readValue(received.payload(), KeyEventData.class);
                        ssePushService.broadcastReadToMachine(guid, "key_event", data);
                    }else if("METRIC".equals(received.taskType())) {

                        log.info("METRIC received {}", received.payload());
                        MetricEventData data = objectMapper.readValue(received.payload(), MetricEventData.class);
                        agentEventHandlerService.handleMetricEvent(received,data);
                        ssePushService.broadcastMetricsToMachine(guid, "metric_event", data);
                    }
                }

                default -> log.info("Unrecognized prefix: {}", received.prefix());
            }
        } catch (Exception e) {
            log.error("Error parsing message: {}", payload, e);
        }
    }
}

