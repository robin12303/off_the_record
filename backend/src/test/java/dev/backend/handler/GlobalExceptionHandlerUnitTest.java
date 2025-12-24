package dev.backend.handler;

import dev.backend.dto.ApiError;
import dev.backend.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBadJson_returns400_andBadJsonCode_andRootMessage() {
        // root cause를 일부러 깊게 만들어서 getMostSpecificCause()가 동작하는지 확인
        Throwable root = new IllegalStateException("ROOT_MSG");
        Throwable wrapper = new RuntimeException("WRAPPER_MSG", root);

        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException(
                        "bad json",
                        wrapper,
                        new MockHttpInputMessage(new byte[0])
                );

        ResponseEntity<ApiError> res = handler.handleBadJson(ex);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        ApiError body = res.getBody();
        assertNotNull(body);

        assertEquals("BAD_JSON", ReflectionTestUtils.getField(body, "code"));
        assertEquals("ROOT_MSG", ReflectionTestUtils.getField(body, "message"));
        assertNotNull(ReflectionTestUtils.getField(body, "timestamp"));
    }

    @Test
    void handleValidation_returns400_andValidationError() {
        MethodArgumentNotValidException ex = Mockito.mock(MethodArgumentNotValidException.class);

        ResponseEntity<ApiError> res = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        ApiError body = res.getBody();
        assertNotNull(body);

        assertEquals("VALIDATION_ERROR", ReflectionTestUtils.getField(body, "code"));
        assertEquals("Invalid request", ReflectionTestUtils.getField(body, "message"));
        assertNotNull(ReflectionTestUtils.getField(body, "timestamp"));
    }

    @Test
    void handleApi_uses_exception_fields() {
        // ApiException 생성자 모르면 mock이 제일 쉬움
        ApiException ex = Mockito.mock(ApiException.class);

        when(ex.getStatus()).thenReturn(HttpStatus.UNAUTHORIZED);
        when(ex.getCode()).thenReturn("AUTH_ERROR");
        when(ex.getMessage()).thenReturn("nope");

        ResponseEntity<ApiError> res = handler.handleApi(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
        ApiError body = res.getBody();
        assertNotNull(body);

        assertEquals("AUTH_ERROR", ReflectionTestUtils.getField(body, "code"));
        assertEquals("nope", ReflectionTestUtils.getField(body, "message"));
        assertNotNull(ReflectionTestUtils.getField(body, "timestamp"));
    }

    @Test
    void handleOther_returns500_andInternalError() {
        ResponseEntity<ApiError> res = handler.handleOther(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
        ApiError body = res.getBody();
        assertNotNull(body);

        assertEquals("INTERNAL_ERROR", ReflectionTestUtils.getField(body, "code"));
        assertEquals("Server error", ReflectionTestUtils.getField(body, "message"));
        assertNotNull(ReflectionTestUtils.getField(body, "timestamp"));
    }
}
