package dev.backend.controller;


import dev.backend.dto.AgentCommandRequest;
import dev.backend.dto.RecentResponse;
import dev.backend.service.AgentPushService;
import dev.backend.service.FrontendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Frontend", description = "프론트에서 호출하는 백엔드 API")
@Slf4j
@RestController
@RequestMapping("/api/backend")
@RequiredArgsConstructor
public class FrontendController {

    private final FrontendService frontendService;
    private final AgentPushService agentPushService;

    @Operation(summary = "최근 에이전트 목록 조회")
    @GetMapping("/recent")
    public List<RecentResponse> recent(){
        return frontendService.recent();
    }

    @Operation(summary = "READ 시작", description = "지정 머신에 READ START 명령을 푸시합니다.")
    @PostMapping("/readStart/{machineGuid}/{commandId}")
    public void readStart(
            @Parameter(description = "대상 머신 GUID", example = "d9f1a8f0-1234-5678-9abc-def012345678")
            @PathVariable String machineGuid,
            @Parameter(description = "명령 ID(추적용)", example = "cmd-001")
            @PathVariable String commandId
    ) {
        log.info("readStart -> machineGuid: {}, commandId: {}", machineGuid, commandId);
        agentPushService.sendCommand(machineGuid, AgentCommandRequest.readStart(machineGuid, commandId));
    }

    @Operation(summary = "READ 중지")
    @PostMapping("/readStop/{machineGuid}/{commandId}")
    public void readStop(
            @Parameter(description = "대상 머신 GUID", example = "d9f1a8f0-1234-5678-9abc-def012345678")
            @PathVariable String machineGuid,
            @Parameter(description = "명령 ID(추적용)", example = "cmd-002")
            @PathVariable String commandId
    ) {
        agentPushService.sendCommand(machineGuid, AgentCommandRequest.readStop(machineGuid, commandId));
    }

    // metricsStart/Stop도 똑같이 붙이면 됨
    @Operation(summary = "METRIC 시작", description = "지정 머신에 METRIC START 명령을 푸시합니다.")
    @PostMapping("/metricsStart/{machineGuid}/{commandId}")
    public void metricsStart(
            @Parameter(description = "대상 머신 GUID", example = "d9f1a8f0-1234-5678-9abc-def012345678")
            @PathVariable String machineGuid,
            @Parameter(description = "명령 ID(추적용)", example = "cmd-001")
            @PathVariable String commandId
    ) {
        log.info("metricstart -> machineGuid: {}, commandId: {}", machineGuid, commandId);
        agentPushService.sendCommand(machineGuid, AgentCommandRequest.metricsStart(machineGuid, commandId));
    }

    @Operation(summary = "METRIC 중지")
    @PostMapping("/metricsStop/{machineGuid}/{commandId}")
    public void metricsStop(
            @Parameter(description = "대상 머신 GUID", example = "d9f1a8f0-1234-5678-9abc-def012345678")
            @PathVariable String machineGuid,
            @Parameter(description = "명령 ID(추적용)", example = "cmd-002")
            @PathVariable String commandId
    ) {
        agentPushService.sendCommand(machineGuid, AgentCommandRequest.metricsStop(machineGuid, commandId));
    }
}
