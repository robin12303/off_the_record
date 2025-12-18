package dev.backend.service;

import dev.backend.dto.ReceivedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PayloadParser {
    private final ObjectMapper objectMapper;

    public ReceivedMessage parseTaskPayload(String payload) {
        try {
            return objectMapper.readValue(payload, ReceivedMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("payload JSON 파싱 실패: " + payload, e);
        }
    }
}
