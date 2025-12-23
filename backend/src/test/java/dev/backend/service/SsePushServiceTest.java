package dev.backend.service;

import dev.backend.components.SseEmitterMetricsRegistry;
import dev.backend.components.SseEmitterReadRegistry;
import dev.backend.dto.KeyEventData;
import dev.backend.dto.MetricEventData;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SsePushServiceTest {

    @Test
    void shouldIncrementSentCount_whenEmitterSendSucceeds() throws Exception {
        SseEmitterReadRegistry readRegistry = mock(SseEmitterReadRegistry.class);
        SseEmitterMetricsRegistry metricsRegistry = mock(SseEmitterMetricsRegistry.class);
        SsePushService service = new SsePushService(readRegistry, metricsRegistry);

        SseEmitter emitter = mock(SseEmitter.class);

        doAnswer(inv -> {
            var consumer = (java.util.function.BiConsumer<String, SseEmitter>) inv.getArgument(1);
            consumer.accept("sub1", emitter);
            return null;
        }).when(readRegistry).forEach(eq("M-1"), any());

        int sent_read = service.broadcastReadToMachine("M-1", "key_event", new KeyEventData(
                "2025:12:22:54",
                "ON",
                "DOWN",
                "a")
                );

        int sent_matric = service.broadcastMetricsToMachine("M-1", "matric_event", new MetricEventData(
                "2025:12:22:54",
                1000L,
                1730000000L,
                24L))
                ;

        assertThat(sent_read).isEqualTo(1);
        assertThat(sent_matric).isEqualTo(1);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }
}