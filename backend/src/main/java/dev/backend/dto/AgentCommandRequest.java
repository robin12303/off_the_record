package dev.backend.dto;


import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AgentCommandRequest", description = "에이전트에게 명령을 전송하는 요청")
public record AgentCommandRequest(

        @Schema(description = "명령 prefix", example = "READ", allowableValues = {"READ", "METRICS", "HEARTBEAT"})
        String prefix,

        @Schema(description = "명령 ID(추적용)", example = "cmd-20251223-0001")
        String commandId,

        @Schema(description = "대상 머신 GUID", example = "d9f1a8f0-1234-5678-9abc-def012345678")
        String machineGuid,

        @Schema(description = "작업 타입", example = "START", allowableValues = {"START", "STOP", "OK"})
        String taskType
) {
    public static AgentCommandRequest readStop(String machineGuid, String commandId) {
        return new AgentCommandRequest("READ", commandId, machineGuid, "STOP");
    }
    public static AgentCommandRequest readStart(String machineGuid, String commandId) {
        return new AgentCommandRequest("READ", commandId, machineGuid, "START");
    }
    public static AgentCommandRequest metricsStart(String machineGuid, String commandId) {
        return new AgentCommandRequest("METRICS", commandId, machineGuid, "START");
    }
    public static AgentCommandRequest metricsStop(String machineGuid, String commandId) {
        return new AgentCommandRequest("METRICS", commandId, machineGuid, "STOP");
    }
    public static AgentCommandRequest hello() {
        return new AgentCommandRequest("HEARTBEAT", "HEARTBEAT", "HEARTBEAT", "OK");
    }
}
