package dev.backend.controller;

import dev.backend.dto.AgentCommandRequest;
import dev.backend.dto.RecentResponse;
import dev.backend.service.AgentPushService;
import dev.backend.service.FrontendService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FrontendControllerUnitTest {

    @Mock FrontendService frontendService;
    @Mock AgentPushService agentPushService;

    @InjectMocks FrontendController controller;

    @Test
    void recent_returns_value_from_service() {
        List<RecentResponse> expected = List.of(); // 필요하면 mock(RecentResponse.class) 넣어도 됨
        when(frontendService.recent()).thenReturn(expected);

        List<RecentResponse> actual = controller.recent();

        assertSame(expected, actual);
        verify(frontendService).recent();
        verifyNoInteractions(agentPushService);
    }

    @Test
    void readStart_calls_sendCommand_with_readStart_request() {
        String machineUuid = "d9f1a8f0-1234-5678-9abc-def012345678";
        String commandId = "cmd-001";

        controller.readStart(machineUuid, commandId);

        verify(agentPushService).sendCommand(
                eq(machineUuid),
                eq(AgentCommandRequest.readStart(machineUuid, commandId))
        );
        verifyNoInteractions(frontendService);
    }

    @Test
    void readStop_calls_sendCommand_with_readStop_request() {
        String machineUuid = "d9f1a8f0-1234-5678-9abc-def012345678";
        String commandId = "cmd-002";

        controller.readStop(machineUuid, commandId);

        verify(agentPushService).sendCommand(
                eq(machineUuid),
                eq(AgentCommandRequest.readStop(machineUuid, commandId))
        );
        verifyNoInteractions(frontendService);
    }
}
