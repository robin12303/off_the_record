package dev.backend.components;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterMetricsRegistryTest {

    @Test
    void add_then_forEach_canFindSameEmitter_andSubId() {
        var registry = new SseEmitterMetricsRegistry();

        String machineGuid = "m1";
        var emitter = new SseEmitter(30_000L);

        String subId = registry.add(machineGuid, emitter);

        var seen = new ConcurrentHashMap<String, SseEmitter>();
        registry.forEach(machineGuid, seen::put);

        assertThat(seen).containsKey(subId);
        // SseEmitter는 equals 오버라이드가 아니라 "동일 객체"로 확인하는 게 명확함
        assertThat(seen.get(subId)).isSameAs(emitter);
    }

    @Test
    void remove_existingSubId_shouldDisappear() {
        var registry = new SseEmitterMetricsRegistry();

        String machineGuid = "m1";
        var emitter = new SseEmitter(30_000L);
        String subId = registry.add(machineGuid, emitter);

        registry.remove(machineGuid, subId);

        var seen = new ConcurrentHashMap<String, SseEmitter>();
        registry.forEach(machineGuid, seen::put);

        assertThat(seen).doesNotContainKey(subId);
        assertThat(seen).isEmpty();
    }

    @Test
    void remove_unknownMachine_or_unknownSubId_shouldNotThrow() {
        var registry = new SseEmitterMetricsRegistry();

        // 아무 일도 안 일어나야 정상
        registry.remove("nope", "nope");

        // 등록 후에도, 틀린 subId 제거해도 예외 없어야 함
        String machineGuid = "m1";
        String subId = registry.add(machineGuid, new SseEmitter());
        registry.remove(machineGuid, "wrong-subid");

        var count = new AtomicInteger(0);
        registry.forEach(machineGuid, (id, em) -> count.incrementAndGet());
        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    void forEach_unknownMachine_shouldNotInvokeConsumer() {
        var registry = new SseEmitterMetricsRegistry();

        var count = new AtomicInteger(0);
        registry.forEach("nope", (id, em) -> count.incrementAndGet());

        assertThat(count.get()).isZero();
    }
}
