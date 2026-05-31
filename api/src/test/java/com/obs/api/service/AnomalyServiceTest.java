package com.obs.api.service;

import com.obs.api.dto.AnomalyDto;
import com.obs.api.entity.Anomaly;
import com.obs.api.entity.AnomalyKey;
import com.obs.api.repository.AnomalyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyServiceTest {

    @Mock AnomalyRepository repo;
    @InjectMocks AnomalyService service;

    private static final LocalDate DAY    = LocalDate.of(1995, 7, 3);
    private static final Instant   WINDOW = Instant.parse("1995-07-03T16:20:00Z");

    @Test
    void forDayMapsAllDtoFieldsCorrectly() {
        Anomaly a = buildAnomaly(DAY, WINDOW, "ip_volume", "10.66.66.66", 1_500L, 500L);
        when(repo.findByKeyDay(DAY)).thenReturn(List.of(a));

        AnomalyDto dto = service.forDay(DAY).get(0);

        assertThat(dto.windowStart()).isEqualTo(WINDOW);
        assertThat(dto.metric()).isEqualTo("ip_volume");
        assertThat(dto.subject()).isEqualTo("10.66.66.66");
        assertThat(dto.value()).isEqualTo(1_500L);
        assertThat(dto.threshold()).isEqualTo(500L);
    }

    @Test
    void forDayReturnsManyAnomalies() {
        List<Anomaly> entities = List.of(
            buildAnomaly(DAY, WINDOW, "ip_volume",    "10.66.66.66", 1_500L, 500L),
            buildAnomaly(DAY, WINDOW, "status_class", "4xx",           600L, 200L)
        );
        when(repo.findByKeyDay(DAY)).thenReturn(entities);

        List<AnomalyDto> result = service.forDay(DAY);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).metric()).isEqualTo("ip_volume");
        assertThat(result.get(1).metric()).isEqualTo("status_class");
    }

    @Test
    void forDayReturnsEmptyListWhenNoAnomalies() {
        when(repo.findByKeyDay(any())).thenReturn(List.of());

        assertThat(service.forDay(DAY)).isEmpty();
    }

    @Test
    void forDayDelegatesToRepositoryWithExactDay() {
        when(repo.findByKeyDay(DAY)).thenReturn(List.of());

        service.forDay(DAY);

        verify(repo).findByKeyDay(DAY);
        verifyNoMoreInteractions(repo);
    }

    private Anomaly buildAnomaly(LocalDate day, Instant window,
                                  String metric, String subject,
                                  long value, long threshold) {
        AnomalyKey key = new AnomalyKey();
        key.setDay(day);
        key.setWindowStart(window);
        key.setMetric(metric);
        key.setSubject(subject);
        Anomaly a = new Anomaly();
        a.setKey(key);
        a.setValue(value);
        a.setThreshold(threshold);
        return a;
    }
}
