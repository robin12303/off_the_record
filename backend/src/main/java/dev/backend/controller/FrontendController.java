package dev.backend.controller;


import dev.backend.dto.AgentCommandRequest;
import dev.backend.dto.RecentResponse;
import dev.backend.service.AgentPushService;
import dev.backend.service.FrontendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/backend")
@RequiredArgsConstructor
public class FrontendController {
    private final FrontendService frontendService;
    private final AgentPushService agentPushService;
    @GetMapping("/recent")
    public List<RecentResponse> recent(){
        return frontendService.recent();
    }

    @PostMapping("/readStart/{machineGuid}/{commandId}")
    public void readStart(@PathVariable String machineGuid, @PathVariable String commandId) {
        log.info("readStart -> machineGuid: {}, commandId: {}", machineGuid, commandId);
        agentPushService.sendCommand(machineGuid, AgentCommandRequest.readStart(machineGuid, commandId));
    }

    @PostMapping("/readStop/{machineGuid}/{commandId}")
    public void readStop(@PathVariable String machineGuid, @PathVariable String commandId) {
        agentPushService.sendCommand(machineGuid, AgentCommandRequest.readStop(machineGuid, commandId));
    }


}
