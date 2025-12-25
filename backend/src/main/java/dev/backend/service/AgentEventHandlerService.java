package dev.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backend.dto.ReceivedMessage;
import dev.backend.repository.AgentMetricsRepository;
import dev.backend.sse.controller.dto.MetricEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional
@Service
@RequiredArgsConstructor
public class AgentEventHandlerService {
    private final AgentMetricsRepository agentMetricsRepository;
    public void MetricEvent(ReceivedMessage received, MetricEventData eventData){
        agentMetricsRepository.insertRequiredOnly(received.machineUuid(),eventData.windowMs(),eventData.windowEndMs(),eventData.keystrokes()
                );
    }
}
