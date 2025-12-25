package dev.backend.components;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WebSocketSessionRegistryTest {

    @Test
    void put_firstTime_shouldStoreSession() {
        var registry = new WebSocketSessionRegistry();

        WebSocketSession s1 = mock(WebSocketSession.class);

        registry.put("m1", s1);

        assertThat(registry.get("m1")).isSameAs(s1);
        verifyNoInteractions(s1); // close 같은 거 호출되면 안 됨
    }

    @Test
    void put_replaceWithDifferentSession_shouldCloseOld_ifOpen() throws Exception {
        var registry = new WebSocketSessionRegistry();

        WebSocketSession old = mock(WebSocketSession.class);
        WebSocketSession next = mock(WebSocketSession.class);

        when(old.isOpen()).thenReturn(true);

        registry.put("m1", old);
        registry.put("m1", next);

        assertThat(registry.get("m1")).isSameAs(next);
        verify(old, times(1)).isOpen();
        verify(old, times(1)).close();
        verifyNoInteractions(next);
    }

    @Test
    void put_replaceWithSameSession_shouldNotClose() throws Exception {
        var registry = new WebSocketSessionRegistry();

        WebSocketSession s1 = mock(WebSocketSession.class);

        registry.put("m1", s1);
        registry.put("m1", s1);

        assertThat(registry.get("m1")).isSameAs(s1);
        verify(s1, never()).close();
        // old == session이면 isOpen()도 호출되지 않아야 함
        verify(s1, never()).isOpen();
    }

    @Test
    void put_replaceButOldNotOpen_shouldNotClose() throws Exception {
        var registry = new WebSocketSessionRegistry();

        WebSocketSession old = mock(WebSocketSession.class);
        WebSocketSession next = mock(WebSocketSession.class);

        when(old.isOpen()).thenReturn(false);

        registry.put("m1", old);
        registry.put("m1", next);

        assertThat(registry.get("m1")).isSameAs(next);
        verify(old, times(1)).isOpen();
        verify(old, never()).close();
    }

    @Test
    void put_closeThrows_shouldBeIgnored() throws Exception {
        var registry = new WebSocketSessionRegistry();

        WebSocketSession old = mock(WebSocketSession.class);
        WebSocketSession next = mock(WebSocketSession.class);

        when(old.isOpen()).thenReturn(true);
        doThrow(new IOException("boom")).when(old).close();

        registry.put("m1", old);

        // 예외 터지면 테스트가 죽어야 하는데, 코드가 무시하니까 살아야 정상
        registry.put("m1", next);

        assertThat(registry.get("m1")).isSameAs(next);
        verify(old, times(1)).close();
    }

    @Test
    void remove_shouldRemoveOnlyIfKeyAndValueMatch() {
        var registry = new WebSocketSessionRegistry();

        WebSocketSession s1 = mock(WebSocketSession.class);
        WebSocketSession s2 = mock(WebSocketSession.class);

        registry.put("m1", s1);

        // 다른 세션으로 remove 시도하면 안 지워져야 함
        registry.remove("m1", s2);
        assertThat(registry.get("m1")).isSameAs(s1);

        // 같은 세션이면 지워져야 함
        registry.remove("m1", s1);
        assertThat(registry.get("m1")).isNull();
    }
}
