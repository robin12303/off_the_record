package dev.backend.service;


import dev.backend.components.SseEmitterRegistry;
import dev.backend.dto.KeyEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class SsePushService {
    private final SseEmitterRegistry registry;

    public int broadcastToMachine(String machineGuid, String eventName, KeyEventData data) {
        var sent = new java.util.concurrent.atomic.AtomicInteger();

        registry.forEach(machineGuid, (subId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
                sent.incrementAndGet();
            } catch (Exception e) {
                registry.remove(machineGuid, subId);
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        });

        return sent.get();
    }
}
