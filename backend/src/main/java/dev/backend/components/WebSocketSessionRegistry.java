package dev.backend.components;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {
    private final Map<String, WebSocketSession> byMachineGuid = new ConcurrentHashMap<>();

    public void put(String machineGuid, WebSocketSession session) {
        WebSocketSession old = byMachineGuid.put(machineGuid, session);
        if (old != null && old != session && old.isOpen()) {
            try { old.close(); } catch (Exception ignored) {}
        }
    }

    public void remove(String machineGuid, WebSocketSession session) {
        byMachineGuid.remove(machineGuid, session);
    }

    public void remove(WebSocketSession session) {
        byMachineGuid.entrySet().removeIf(e -> e.getValue() == session);
    }

    public WebSocketSession get(String machineGuid) {
        return byMachineGuid.get(machineGuid);
    }
}
