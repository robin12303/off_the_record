package dev.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backend.components.WebSocketSessionRegistry;
import dev.backend.dto.HeartBeat;
import dev.backend.dto.ReceivedMessage;
import dev.backend.repository.AgentCommandLogRepository;
import dev.backend.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentHandlerService {

    public static final String ATTR_MACHINE_UUID = "machineUuid";

    private final AgentRepository agentRepository;
    private final AgentCommandLogRepository agentCommandLogRepository;
    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;

    /**
     * HEARTBEAT: 계속 받되, machineUuid는 최초 1회만 바인딩.
     * 이후 mismatch면 바로 킥.
     */
    @Transactional
    public void HeartBeat(WebSocketSession session, ReceivedMessage received) {
        String incomingUuid = received.machineUuid();
        String boundUuid = bindOrValidateUuid(session, incomingUuid);
        if (boundUuid == null) return; // 킥됨

        String ipAddress = extractIpOnly(session);

        try {
            HeartBeat hb = objectMapper.readValue(received.payload(), HeartBeat.class);

            // payload에도 uuid가 있다면 bound 기준으로 검증
            String hbUuid = hb.machineUuid();
            if (hbUuid == null || hbUuid.isBlank() || !boundUuid.equals(hbUuid)) {
                kick(session, "HEARTBEAT payload uuid mismatch", boundUuid, hbUuid);
                return;
            }

            LocalDateTime ts = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());

            agentRepository.upsertByMachineUuid(
                    boundUuid,        // ✅ hbUuid 대신 boundUuid로 통일
                    ipAddress,
                    hb.hostName(),
                    hb.cpuName(),
                    hb.gpuName(),
                    hb.ramTotalMb(),
                    hb.osName(),
                    hb.osVersion(),
                    ts
            );

        } catch (Exception e) {
            // payload 전체 로그는 폭탄/민감정보 위험. 필요하면 길이 제한 추천.
            log.warn("HEARTBEAT payload parse failed. sessionId={}", session.getId(), e);
        }
    }

    /**
     * METRICS START/STOP 같은 "커맨드 응답" 처리
     * received.machineUuid() 대신 boundUuid를 DB에 기록.
     */
    @Transactional
    public void MetricsCommand(WebSocketSession session, ReceivedMessage received) {
        String boundUuid = requireBoundUuid(session);
        if (boundUuid == null) return;

        String taskType = received.taskType();
        if (taskType == null) {
            log.info("[METRICS] taskType is null. machineUuid={}", boundUuid);
            return;
        }

        switch (taskType) {
            case "START" -> agentCommandLogRepository.upsertByCommandId(
                    "METRICS", received.commandId(), boundUuid, "START", "ACCEPTED"
            );
            case "STOP" -> agentCommandLogRepository.upsertByCommandId(
                    "METRICS", received.commandId(), boundUuid, "STOP", "ACCEPTED"
            );
            default -> log.info("[METRICS] Unrecognized taskType={}. machineUuid={}", taskType, boundUuid);
        }
    }

    /**
     * 최초 1회만 바인딩. 이미 바인딩되어 있으면 incoming과 비교.
     * - incoming이 null/blank면 킥
     * - mismatch면 킥
     */
    private String bindOrValidateUuid(WebSocketSession session, String incomingUuid) {
        if (incomingUuid == null || incomingUuid.isBlank()) {
            kick(session, "missing machineUuid", null, incomingUuid);
            return null;
        }

        String boundUuid = (String) session.getAttributes().get(ATTR_MACHINE_UUID);

        if (boundUuid == null) {
            // ✅ 최초 1회 바인딩
            session.getAttributes().put(ATTR_MACHINE_UUID, incomingUuid);
            registry.put(incomingUuid, session);
            log.info("Bound machineUuid: sessionId={}, machineUuid={}", session.getId(), incomingUuid);
            return incomingUuid;
        }

        // ✅ 바인딩 이후엔 변경 금지
        if (!Objects.equals(boundUuid, incomingUuid)) {
            kick(session, "machineUuid mismatch", boundUuid, incomingUuid);
            return null;
        }

        return boundUuid;
    }

    /**
     * HEARTBEAT 전에 온 메시지들은 원칙적으로 거부.
     */
    private String requireBoundUuid(WebSocketSession session) {
        String boundUuid = (String) session.getAttributes().get(ATTR_MACHINE_UUID);
        if (boundUuid == null) {
            kick(session, "message before binding", null, null);
            return null;
        }
        return boundUuid;
    }

    private void kick(WebSocketSession session, String reason, String boundUuid, String incomingUuid) {
        log.warn("KICK: reason={} sessionId={} boundUuid={} incomingUuid={}",
                reason, session.getId(), boundUuid, incomingUuid);

        if (boundUuid != null) {
            registry.remove(boundUuid, session);
        }

        try {
            session.close(CloseStatus.POLICY_VIOLATION); // 1008
        } catch (IOException e) {
            log.warn("Failed to close session {}", session.getId(), e);
        }
    }

    private static String extractIpOnly(WebSocketSession session) {
        InetSocketAddress remote = session.getRemoteAddress();
        if (remote == null) return null;

        if (remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return remote.getHostString();
    }
}
