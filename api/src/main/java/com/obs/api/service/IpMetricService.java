package com.obs.api.service;

import com.obs.api.dto.IpMetricDto;
import com.obs.api.repository.IpMetricRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IpMetricService {

    private final IpMetricRepository repo;

    public IpMetricService(IpMetricRepository repo) { this.repo = repo; }

    public List<IpMetricDto> range(String ip, Instant from, Instant to) {
        return repo.findRange(ip, from, to).stream()
            .map(m -> new IpMetricDto(m.getClientIp(), m.getWindowStart(),
                                      m.getRequestCount()))
            .toList();
    }
}
