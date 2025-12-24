package dev.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backend.components.WebSocketSessionRegistry;
import dev.backend.dto.AgentCommandRequest;
import dev.backend.repository.AgentCommandLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentPushServiceUnitTest {

    @Mock WebSocketSessionRegistry registry;
    @Mock AgentCommandLogRepository agentCommandLogRepository;
    @Mock ObjectMapper om;
    @Mock WebSocketSession session;

    @Test
    void sendCommand_sessionNull_returnsFalse() {
        AgentPushService sut = new AgentPushService(registry, agentCommandLogRepository, om);

        when(registry.get("m1")).thenReturn(null);

        boolean ok = sut.sendCommand("m1", AgentCommandRequest.readStop("m1", "cmd-1"));

        assertFalse(ok);
        verify(registry).get("m1");
        verifyNoMoreInteractions(registry);
        verifyNoInteractions(session, om, agentCommandLogRepository);
    }

    @Test
    void sendCommand_sessionClosed_returnsFalse() {
        AgentPushService sut = new AgentPushService(registry, agentCommandLogRepository, om);

        when(registry.get("m1")).thenReturn(session);
        when(session.isOpen()).thenReturn(false);

        boolean ok = sut.sendCommand("m1", AgentCommandRequest.readStop("m1", "cmd-1"));

        assertFalse(ok);
        verify(registry).get("m1");
        verify(session).isOpen();
        verifyNoInteractions(om, agentCommandLogRepository);
        verify(registry, never()).remove(anyString(), any());
    }

    @Test
    void sendCommand_success_sendsMessage_and_upsertsLog_and_returnsTrue() throws Exception {
        AgentPushService sut = new AgentPushService(registry, agentCommandLogRepository, om);

        String machineGuid = "m1";
        String commandId = "cmd-1";
        AgentCommandRequest cmd = AgentCommandRequest.readStop(machineGuid, commandId);

        when(registry.get(machineGuid)).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        when(om.writeValueAsString(cmd)).thenReturn("{\"ok\":true}");

        boolean ok = sut.sendCommand(machineGuid, cmd);

        assertTrue(ok);

        // ✅ 메시지 payload 검증
        ArgumentCaptor<TextMessage> msgCap = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(msgCap.capture());
        assertEquals("{\"ok\":true}", msgCap.getValue().getPayload());

        // ✅ 로그 upsert 호출 검증 (4번째 인자는 너 record 필드명에 따라 다를 수 있어서 anyString 처리)
        verify(agentCommandLogRepository).upsertByCommandId(
                eq(cmd.prefix()),
                eq(cmd.commandId()),
                eq(cmd.machineGuid()),
                anyString(),              // 보통 cmd.taskType() 자리 ("STOP"/"START"/"OK")
                eq("PENDING")
        );

        verify(registry, never()).remove(anyString(), any());
    }

    @Test
    void sendCommand_whenSendFails_removesSession_and_returnsFalse() throws Exception {
        AgentPushService sut = new AgentPushService(registry, agentCommandLogRepository, om);

        String machineGuid = "m1";
        AgentCommandRequest cmd = AgentCommandRequest.readStop(machineGuid, "cmd-1");

        when(registry.get(machineGuid)).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        when(om.writeValueAsString(cmd)).thenReturn("{\"ok\":true}");

        doThrow(new RuntimeException("boom"))
                .when(session).sendMessage(any(TextMessage.class));

        boolean ok = sut.sendCommand(machineGuid, cmd);

        assertFalse(ok);
        verify(registry).remove(machineGuid, session);
        verify(agentCommandLogRepository, never()).upsertByCommandId(any(), any(), any(), any(), any());
    }
}
