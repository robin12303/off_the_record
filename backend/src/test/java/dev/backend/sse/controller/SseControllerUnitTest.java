package dev.backend.sse.controller;


import dev.backend.components.SseEmitterMetricsRegistry;
import dev.backend.components.SseEmitterReadRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SseControllerUnitTest {

    @Mock SseEmitterReadRegistry registry;
    @Mock SseEmitterMetricsRegistry metricsRegistry;

    @Test
    void streamMetrics_registers_and_cleanup_removes() throws Exception {
        when(metricsRegistry.add(eq("m2"), any(SseEmitter.class))).thenReturn("sub-m-1");

        SseController controller = new SseController(registry, metricsRegistry);

        SseEmitter emitter = controller.streamMetrics("m2");

        verify(metricsRegistry).add(eq("m2"), same(emitter));

        // ✅ 스프링 MVC 없이도 cleanup이 remove를 부르는지 검증 (강제로 completion 콜백 실행)
        fireCompletionCallback(emitter);

        verify(metricsRegistry).remove("m2", "sub-m-1");
        verifyNoInteractions(registry);
    }

    /** Spring MVC가 없어서 emitter.complete()로 콜백이 안 도는 환경을 위한 우회 */
    private static void fireCompletionCallback(SseEmitter emitter) throws Exception {
        // SseEmitter -> ResponseBodyEmitter 쪽에 completionCallback 필드가 있음(버전에 따라 이름은 비슷함)
        Object completion = null;

        Class<?> c = emitter.getClass().getSuperclass(); // ResponseBodyEmitter
        for (var f : c.getDeclaredFields()) {
            if (f.getName().toLowerCase().contains("completion")) {
                f.setAccessible(true);
                completion = f.get(emitter);
                break;
            }
        }

        if (completion == null) {
            throw new IllegalStateException("completion callback field not found (Spring 버전 확인 필요)");
        }

        // 보통 Runnable처럼 run()이 있음
        Method run = completion.getClass().getDeclaredMethod("run");
        run.setAccessible(true);
        run.invoke(completion);
    }
}
