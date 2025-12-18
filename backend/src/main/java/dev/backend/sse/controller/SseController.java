package dev.backend.sse.controller;


import dev.backend.components.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterRegistry registry;

    @GetMapping(value = "/stream/{machineGuid}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String machineGuid) {

        SseEmitter emitter = new SseEmitter(0L);
        String subId = registry.add(machineGuid, emitter);

        Runnable cleanup = () -> registry.remove(machineGuid, subId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // 연결 확인용 1회 (선택)
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            cleanup.run();
            emitter.completeWithError(e);
        }

        return emitter;
    }
}