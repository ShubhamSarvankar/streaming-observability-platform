package com.obs.api.service;

import com.obs.api.dto.AnomalyDto;
import com.obs.api.repository.AnomalyRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AnomalyService {

    private final AnomalyRepository repo;

    public AnomalyService(AnomalyRepository repo) { this.repo = repo; }

    public List<AnomalyDto> forDay(LocalDate day) {
        return repo.findByKeyDay(day).stream()
            .map(a -> new AnomalyDto(a.getKey().getWindowStart(),
                                     a.getKey().getMetric(),
                                     a.getKey().getSubject(),
                                     a.getValue(),
                                     a.getThreshold()))
            .toList();
    }
}
