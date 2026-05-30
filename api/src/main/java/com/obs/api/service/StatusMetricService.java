package com.obs.api.service;

import com.obs.api.dto.StatusMetricDto;
import com.obs.api.repository.StatusMetricRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StatusMetricService {

    private final StatusMetricRepository repo;

    public StatusMetricService(StatusMetricRepository repo) { this.repo = repo; }

    public List<StatusMetricDto> range(String statusClass, Instant from, Instant to) {
        return repo.findRange(statusClass, from, to).stream()
            .map(m -> new StatusMetricDto(m.getStatusClass(), m.getWindowStart(),
                                          m.getCount()))
            .toList();
    }
}
