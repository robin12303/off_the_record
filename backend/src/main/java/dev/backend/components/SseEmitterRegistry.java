package dev.backend.components;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseEmitterRegistry {
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, SseEmitter>> byMachine = new ConcurrentHashMap<>();

    public String add(String machineGuid, SseEmitter emitter) {
        String subId = java.util.UUID.randomUUID().toString();
        byMachine.computeIfAbsent(machineGuid, k -> new ConcurrentHashMap<>())
                .put(subId, emitter);
        return subId;
    }

    public void remove(String machineGuid, String subId) {
        var map = byMachine.get(machineGuid);
        if (map == null) return;
        map.remove(subId);
        if (map.isEmpty()) byMachine.remove(machineGuid);
    }

    public void forEach(String machineGuid, java.util.function.BiConsumer<String, SseEmitter> fn) {
        var map = byMachine.get(machineGuid);
        if (map == null) return;
        map.forEach(fn); // (subId, emitter)
    }
}
