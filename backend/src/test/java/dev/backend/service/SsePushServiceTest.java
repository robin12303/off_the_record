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

        SseEmitter emitterRead = mock(SseEmitter.class);
        SseEmitter emitterMetrics = mock(SseEmitter.class);

        // read registry stub
        doAnswer(inv -> {
            var consumer = (java.util.function.BiConsumer<String, SseEmitter>) inv.getArgument(1);
            consumer.accept("sub1", emitterRead);
            return null;
        }).when(readRegistry).forEach(eq("M-1"), any());

        // metrics registry stub
        doAnswer(inv -> {
            var consumer = (java.util.function.BiConsumer<String, SseEmitter>) inv.getArgument(1);
            consumer.accept("subM1", emitterMetrics);
            return null;
        }).when(metricsRegistry).forEach(eq("M-1"), any());

        int sentRead = service.broadcastReadToMachine(
                "M-1", "key_event",
                new KeyEventData("2025:12:22:54", "ON", "DOWN", "a")
        );

        int sentMetrics = service.broadcastMetricsToMachine(
                "M-1", "metric_event",
                new MetricEventData("2025:12:22:54", 1000L, 1730000000L, 24L)
        );

        assertThat(sentRead).isEqualTo(1);
        assertThat(sentMetrics).isEqualTo(1);

        verify(emitterRead, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitterMetrics, times(1)).send(any(SseEmitter.SseEventBuilder.class));

        verify(readRegistry).forEach(eq("M-1"), any());
        verify(metricsRegistry).forEach(eq("M-1"), any());
    }

}