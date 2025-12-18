package dev.backend.service;
import dev.backend.components.WebSocketSessionRegistry;
import dev.backend.dto.AgentCommandRequest;
import dev.backend.repository.AgentCommandLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPushService {
    private final WebSocketSessionRegistry registry;
    private final AgentCommandLogRepository agentCommandLogRepository;
    private final ObjectMapper om;
    public boolean sendCommand(String machineGuid, AgentCommandRequest command) {
        WebSocketSession session = registry.get(machineGuid);
        if (session == null || !session.isOpen()) return false;
        try{
            String json = om.writeValueAsString(command);

            synchronized (session) {
                session.sendMessage(new TextMessage(json));
                agentCommandLogRepository.upsertByCommandId(
                        command.prefix(),
                        command.commandId(),
                        command.machineGuid(),
                        command.taskType(),
                        "PENDING"
                );
                log.info("Sent command {}", command);
            }

            return true;
        }catch(Exception e){
            // 실패하면 registry에서 정리하는 게 보통 낫다(죽은 세션일 가능성 큼)
            log.error(e.getMessage());
            log.info("ERROR command {}", e.getMessage());
            registry.remove(machineGuid,session); // remove(WebSocketSession) 같은 메서드가 있어야 함
            return false;
        }
    }
}
