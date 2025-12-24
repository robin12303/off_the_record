package dev.backend.sse.controller;

import dev.backend.components.SseEmitterMetricsRegistry;
import dev.backend.components.SseEmitterReadRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "SSE", description = "Server-Sent Events 구독 API")
@Slf4j
@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterReadRegistry registry;
    private final SseEmitterMetricsRegistry metricsRegistry;

    private SseEmitter subscribe(
            String machineUuid,
            BiFunction<String, SseEmitter, String> add,
            BiConsumer<String, String> remove
    ) {
        SseEmitter emitter = new SseEmitter(0L);
        String subId = add.apply(machineUuid, emitter);

        AtomicBoolean cleaned = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            if (cleaned.compareAndSet(false, true)) {
                remove.accept(machineUuid, subId);
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
            return emitter;
        }

        return emitter;
    }

    @Operation(
            summary = "READ SSE 구독",
            description = """
            특정 machineUuid의 READ 스트림을 구독합니다.
            클라이언트는 EventSource로 접속하세요.
            이벤트 예: connected, (이후 read 이벤트들...)
            """
    )
    @ApiResponse(
            responseCode = "200",
            description = "SSE 스트림(text/event-stream)",
            content = @Content(
                    mediaType = "text/event-stream",
                    schema = @Schema(type = "string", description = "SSE 프레임(event/data 라인)"),
                    examples = @ExampleObject(
                            name = "connected event",
                            value = "event: connected\ndata: ok\nretry: 3000\n\n"
                    )
            )
    )
    @GetMapping(value = "/stream/read/{machineUuid}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String machineUuid) {
        return subscribe(machineUuid, registry::add, registry::remove);
    }

    @Operation(
            summary = "METRICS SSE 구독",
            description = "특정 machineGuid의 METRICS 스트림을 구독합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "SSE 스트림(text/event-stream)",
            content = @Content(
                    mediaType = "text/event-stream",
                    schema = @Schema(type = "string"),
                    examples = @ExampleObject(
                            name = "connected event",
                            value = "event: connected\ndata: ok\nretry: 3000\n\n"
                    )
            )
    )
    @GetMapping(value = "/stream/metrics/{machineUuid}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics(@PathVariable String machineUuid) {
        return subscribe(machineUuid, metricsRegistry::add, metricsRegistry::remove);
    }
}
