package dev.backend.controller;

import dev.backend.dto.AgentCommandRequest;
import dev.backend.service.FrontendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FrontendControllerStandaloneTest {

    private MockMvc mockMvc;
    private FrontendService frontendService;

    @BeforeEach
    void setUp() {
        frontendService = mock(FrontendService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FrontendController(frontendService)).build();
    }

    @Test
    void metricsStart_sendsExpectedRequest() throws Exception {
        String machineUuid = "m1";
        String commandId = "cmd-001";

        mockMvc.perform(post("/api/backend/metricsStart/{machineUuid}/{commandId}", machineUuid, commandId))
                .andExpect(status().isOk());

        ArgumentCaptor<AgentCommandRequest> captor = ArgumentCaptor.forClass(AgentCommandRequest.class);
        verify(frontendService).sendCommand(eq(machineUuid), captor.capture());

        assertEquals(AgentCommandRequest.metricsStart(machineUuid, commandId), captor.getValue());
        verifyNoMoreInteractions(frontendService);
    }

    @Test
    void metricsStop_sendsExpectedRequest() throws Exception {
        String machineUuid = "m1";
        String commandId = "cmd-002";

        mockMvc.perform(post("/api/backend/metricsStop/{machineUuid}/{commandId}", machineUuid, commandId))
                .andExpect(status().isOk());

        ArgumentCaptor<AgentCommandRequest> captor = ArgumentCaptor.forClass(AgentCommandRequest.class);
        verify(frontendService).sendCommand(eq(machineUuid), captor.capture());

        assertEquals(AgentCommandRequest.metricsStop(machineUuid, commandId), captor.getValue());
        verifyNoMoreInteractions(frontendService);
    }
}

