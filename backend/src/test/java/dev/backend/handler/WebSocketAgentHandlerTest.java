package dev.backend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backend.components.WebSocketSessionRegistry;
import dev.backend.service.AgentHandlerService;
import dev.backend.service.AgentPushService;
import dev.backend.sse.service.SsePushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.Mockito.*;

public class WebSocketAgentHandlerTest {
    private WebSocketSessionRegistry registry;
    private ObjectMapper objectMapper;
    private AgentHandlerService agentHandlerService;
    private SsePushService ssePushService;
    private AgentPushService agentPushService;

    private WebSocketAgentHandler handler;

    @BeforeEach
    void setUp() {
        registry = mock(WebSocketSessionRegistry.class);
        objectMapper = new ObjectMapper();
        agentHandlerService = mock(AgentHandlerService.class);
        ssePushService = mock(SsePushService.class);
        agentPushService = mock(AgentPushService.class);

        handler = new WebSocketAgentHandler(registry, objectMapper, agentHandlerService, ssePushService, agentPushService);
    }


    @Test
    void shouldRegisterSessionAndCallService_whenHeartbeatMessage() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new java.util.HashMap<>());

        String msg = """
        {
          "prefix":"HEARTBEAT",
          "commandId":"c1",
          "machineGuid":"M-123",
          "timestamp":"t",
          "taskType":"PING",
          "payload":"{}"
        }
        """;

        handler.handleTextMessage(session, new TextMessage(msg));

        verify(registry).put(eq("M-123"), eq(session));
        verify(agentHandlerService).handleHeartBeat(eq(session), any());
    }

    @Test
    void shouldNotThrow_whenInvalidJson() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new java.util.HashMap<>());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                handler.handleTextMessage(session, new TextMessage("{not json"))
        );

        verifyNoInteractions(agentHandlerService);
    }

}
