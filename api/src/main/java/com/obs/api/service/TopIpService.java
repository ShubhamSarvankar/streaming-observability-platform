package com.obs.api.service;

import com.obs.api.dto.TopIpDto;
import com.obs.api.repository.TopIpRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TopIpService {

    private final TopIpRepository repo;

    public TopIpService(TopIpRepository repo) { this.repo = repo; }

    public List<TopIpDto> top(Instant window, int n) {
        return repo.findTop(window, n).stream()
            .map(t -> new TopIpDto(t.getKey().getClientIp(), t.getKey().getRequestCount()))
            .toList();
    }
}
