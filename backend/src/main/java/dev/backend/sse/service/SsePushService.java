package dev.backend.sse.service;


import dev.backend.components.SseEmitterMetricsRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class SsePushService {
    private final SseEmitterMetricsRegistry sseEmitterMetricsRegistry;

    public int broadcastMetricsToMachine(String machineUuid, String eventName, Object data) {
        var sent = new java.util.concurrent.atomic.AtomicInteger();

        sseEmitterMetricsRegistry.forEach(machineUuid, (subId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
                sent.incrementAndGet();
            } catch (Exception e) {
                sseEmitterMetricsRegistry.remove(machineUuid, subId);
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        });

        return sent.get();
    }
}
