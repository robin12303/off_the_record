package dev.backend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backend.components.WebSocketSessionRegistry;
import dev.backend.dto.KeyEventData;
import dev.backend.dto.ReceivedMessage;
import dev.backend.service.AgentService;
import dev.backend.service.SsePushService;
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
    private final AgentService agentService;
    private final SsePushService ssePushService;

    // machineGuid별 limiter
    private final ConcurrentHashMap<String, RateLimiter> keyLimiters = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String machineGuid = (String) session.getAttributes().get("machineGuid");
        if (machineGuid != null) {
            registry.remove(machineGuid, session);
            keyLimiters.remove(machineGuid); // ✅ 메모리 정리
        }
        log.info("Closed {} status={}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            ReceivedMessage received = objectMapper.readValue(payload, ReceivedMessage.class);

            switch (received.prefix()) {
                case "READ" -> agentService.handleStartRead(session, received);

                case "HEARTBEAT" -> {
                    session.getAttributes().put("machineGuid", received.machineGuid());
                    registry.put(received.machineGuid(), session);
                    agentService.handleHeartBeat(session, received);
                }

                case "EVENT" -> {
                    session.getAttributes().put("machineGuid", received.machineGuid());

                    if ("KEY".equals(received.taskType())) {
                        String guid = received.machineGuid();

                        // 예: 초당 60개 허용 (너무 빡세면 30~120 사이로 조절)
                        RateLimiter limiter = keyLimiters.computeIfAbsent(guid, g -> RateLimiter.create(60.0));

                        // ✅ 제한 초과면 드롭
                        if (!limiter.tryAcquire()) {
                            return;
                        }

                        KeyEventData data = objectMapper.readValue(received.payload(), KeyEventData.class);
                        ssePushService.broadcastToMachine(guid, "keyevent", data);
                    }
                }

                default -> log.info("Unrecognized prefix: {}", received.prefix());
            }
        } catch (Exception e) {
            log.error("Error parsing message: {}", payload, e);
        }
    }
}

