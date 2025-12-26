package dev.backend.handler;

import dev.backend.dto.ApiError;
import dev.backend.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleBadJson(HttpMessageNotReadableException e) {
        Throwable root = e.getMostSpecificCause();
        log.warn("HttpMessageNotReadableException root={}", root.getMessage(), e);

        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_JSON", root.getMessage(), Instant.now()));
    }

    // ✅ @Valid 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity
                .badRequest()
                .body(new ApiError("VALIDATION_ERROR", "Invalid request", Instant.now()));
    }

    // ✅ 너가 던지는 커스텀 예외
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(new ApiError(e.getCode(), e.getMessage(), Instant.now()));
    }

    // ✅ 그 외 전부 (운영은 message 숨기는 게 보통)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .internalServerError()
                .body(new ApiError("INTERNAL_ERROR", "Server error", Instant.now()));
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncNotUsable(AsyncRequestNotUsableException e) {
        // 클라이언트가 연결 끊은 케이스: 보통 응답 줄 필요도 없음
        return ResponseEntity.noContent().build();
    }
}
