package dev.backend.service;

import dev.backend.components.SseEmitterRegistry;
import dev.backend.dto.KeyEventData;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SsePushServiceTest {

    @Test
    void shouldIncrementSentCount_whenEmitterSendSucceeds() throws Exception {
        SseEmitterRegistry registry = mock(SseEmitterRegistry.class);
        SsePushService service = new SsePushService(registry);

        SseEmitter emitter = mock(SseEmitter.class);

        doAnswer(inv -> {
            var consumer = (java.util.function.BiConsumer<String, SseEmitter>) inv.getArgument(1);
            consumer.accept("sub1", emitter);
            return null;
        }).when(registry).forEach(eq("M-1"), any());

        int sent = service.broadcastToMachine("M-1", "keyevent", new KeyEventData(
                "2025:12:22:54",
                "ON",
                "DOWN",
                "a")
                );

        assertThat(sent).isEqualTo(1);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }
}