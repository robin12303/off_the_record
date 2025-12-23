package dev.backend.sse.controller;


import dev.backend.components.SseEmitterMetricsRegistry;
import dev.backend.components.SseEmitterReadRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

@Slf4j
@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterReadRegistry registry;
    private final SseEmitterMetricsRegistry metricsRegistry;
    private SseEmitter subscribe(String machineGuid,
                                 BiFunction<String, SseEmitter, String> add,
                                 BiConsumer<String, String> remove) {

        SseEmitter emitter = new SseEmitter(0L); // emitter 레벨 timeout
        String subId = add.apply(machineGuid, emitter);

        AtomicBoolean cleaned = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            if (cleaned.compareAndSet(false, true)) {
                remove.accept(machineGuid, subId);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("ok")
                    .reconnectTime(3000L));
        } catch (Exception e) {
            cleanup.run();
            emitter.completeWithError(e);
            return emitter; // 여기서 끝
        }

        return emitter;
    }

    @GetMapping(value = "/stream/read/{machineGuid}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String machineGuid) {
        return subscribe(machineGuid, registry::add, registry::remove);
    }

    @GetMapping(value = "/stream/metrics/{machineGuid}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics(@PathVariable String machineGuid) {
        return subscribe(machineGuid, metricsRegistry::add, metricsRegistry::remove);
    }

}