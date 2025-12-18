package dev.backend.service;


import dev.backend.components.WebSocketSessionRegistry;
import dev.backend.dto.HeartBeat;
import dev.backend.repository.AgentCommandLogRepository;
import dev.backend.repository.AgentRepository;
import dev.backend.components.CommandIdGenerator;
import dev.backend.dto.ReceivedMessage;
import dev.backend.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Transactional
@Service
@RequiredArgsConstructor
public class AgentService {
    private final AgentRepository agentRepository;
    private final AgentCommandLogRepository agentCommandLogRepository;
    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;
    public void handleStartRead(WebSocketSession session, ReceivedMessage received ) {

    }
    public void handleStopRead(WebSocketSession session, ReceivedMessage received ) {

    }
    public void handleHeartBeat(WebSocketSession session, ReceivedMessage received ) {
        String attrGuid = (String) session.getAttributes().get("machineGuid");
        String msgGuid  = received.machineGuid();
        String ipAddress = extractIpOnly(session);
        log.info("attrGuid(session): {}", attrGuid);
        log.info("msgGuid(received): {}", msgGuid);
        log.info("ipAddress(session): {}", ipAddress);
        if(!Objects.equals(attrGuid, msgGuid)) {
            kick(session, attrGuid, msgGuid);
            return;
        }

        try{
            // payload(JSON 텍스트 문자열) → HeartBeat record
            HeartBeat hb = objectMapper.readValue(received.payload(), HeartBeat.class);

            if(!Objects.equals(hb.machineGuid(), msgGuid)) {
                kick(session, attrGuid, msgGuid);
                return;
            }
            if (hb.machineGuid() == null || hb.machineGuid().isBlank()) {
                log.warn("HB parse suspicious (machineGuid missing). payload={}", received.payload());
                return;
            }
            // 필드별로 보기 (원하면 이게 더 명확)
            log.info("HB fields machineGuid={} hostName={} cpuName={} gpuName={} ramTotalMb={} osName={} osVersion={}",
                    hb.machineGuid(), hb.hostName(), hb.cpuName(), hb.gpuName(),
                    hb.ramTotalMb(), hb.osName(), hb.osVersion());

            LocalDateTime ts = TimeUtil.parseTs(received.timestamp());
            agentRepository.upsertByMachineGuid(
                    hb.machineGuid(),
                    ipAddress,
                    hb.hostName(),
                    hb.cpuName(),
                    hb.gpuName(),
                    hb.ramTotalMb(),
                    hb.osName(),
                    hb.osVersion(),
                    ts
            );

        }catch(Exception e){
            log.warn("HEARTBEAT payload parse failed. sessionId={} payload={}",
                    session.getId(), received.payload(), e);
        }
    }

    private void kick(WebSocketSession session, String attrGuid, String msgGuid) {
        log.warn("machineGuid mismatch. sessionId={} attrGuid={} msgGuid={}",
                session.getId(), attrGuid, msgGuid);

        if (attrGuid != null) registry.remove(attrGuid, session);

        try {
            session.close(CloseStatus.POLICY_VIOLATION); // 1008
        } catch (IOException e) {
            log.warn("Failed to close session {}", session.getId(), e);
        }
    }
    private static String extractIpOnly(WebSocketSession session) {
        InetSocketAddress remote = session.getRemoteAddress();
        if (remote == null) return null;

        // ✅ 이게 진짜 IP 문자열 (대괄호/포트/슬래시 없음)
        if (remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }

        // 가끔 address가 null이면 fallback
        return remote.getHostString();
    }
}
