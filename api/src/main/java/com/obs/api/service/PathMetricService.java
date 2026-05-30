package com.obs.api.service;

import com.obs.api.dto.PathMetricDto;
import com.obs.api.repository.PathMetricRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PathMetricService {

    private final PathMetricRepository repo;

    public PathMetricService(PathMetricRepository repo) { this.repo = repo; }

    public List<PathMetricDto> range(String path, Instant from, Instant to) {
        return repo.findRange(path, from, to).stream()
            .map(m -> new PathMetricDto(m.getPath(), m.getWindowStart(),
                                        m.getHitCount(), m.getByteTotal()))
            .toList();
    }
}
