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

@Slf4j
@RequiredArgsConstructor
@Component
public class WebSocketAgentHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;   // ✅ DI
    private final AgentService agentService;
    private final SsePushService ssePushService;
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Connected to {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String machineGuid = (String) session.getAttributes().get("machineGuid");
        if (machineGuid != null) {
            registry.remove(machineGuid, session);
        }
        log.info("Closed {} status={}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.info("Received message: {}", payload);

        try {
            ReceivedMessage received = objectMapper.readValue(payload, ReceivedMessage.class);

            switch (received.prefix()) {
                case "READ" -> agentService.handleStartRead(session, received);
                case "HEARTBEAT" -> {
                    session.getAttributes().put("machineGuid", received.machineGuid()); // ✅ remove 가능해짐
                    registry.put(received.machineGuid(), session);
                    agentService.handleHeartBeat(session, received);
                }
                case "EVENT" -> {
                    session.getAttributes().put("machineGuid", received.machineGuid());

                    if ("KEY".equals(received.taskType())) {
                        KeyEventData data = objectMapper.readValue(received.payload(), KeyEventData.class);
                        // ✅ 여기서 SSE 구독자들에게만 전송 (machineGuid 채널)
                        ssePushService.broadcastToMachine(received.machineGuid(), "keyevent", data);
                    }
                }

                default -> log.info("Unrecognized prefix: {}", received.prefix());
            }
        } catch (Exception e) {
            log.error("Error parsing message: {}", payload, e);
        }
    }
}
