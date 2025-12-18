package dev.backend.components;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CommandIdGenerator {
    // 또는 UUID 사용
    public String generateUUID(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}