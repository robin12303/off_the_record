package dev.backend.service;

import dev.backend.dto.RecentResponse;
import dev.backend.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
@Service
@RequiredArgsConstructor
public class FrontendService {
    private final AgentRepository agentRepository;


    public List<RecentResponse> recent(){
        return agentRepository
                .findAllByOrderByLastSeenAtDesc(PageRequest.of(0,100))
                .getContent()
                .stream()
                .map(RecentResponse::from)
                .toList();
    }
}
