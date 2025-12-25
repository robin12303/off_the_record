package dev.backend.components;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {
    private final Map<String, WebSocketSession> byMachineUuid = new ConcurrentHashMap<>();

    public void put(String machineGuid, WebSocketSession session) {
        WebSocketSession old = byMachineUuid.put(machineGuid, session);
        if (old != null && old != session && old.isOpen()) {
            try { old.close(); } catch (Exception ignored) {}
        }
    }

    public void remove(String machineUuid, WebSocketSession session) {
        byMachineUuid.remove(machineUuid, session);
    }

    public WebSocketSession get(String machineGuid) {
        return byMachineUuid.get(machineGuid);
    }
}
