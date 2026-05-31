package com.obs.api.service;

import com.obs.api.dto.StatusMetricDto;
import com.obs.api.entity.StatusMetric;
import com.obs.api.repository.StatusMetricRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusMetricServiceTest {

    @Mock StatusMetricRepository repo;
    @InjectMocks StatusMetricService service;

    private static final Instant FROM = Instant.parse("1995-07-01T00:00:00Z");
    private static final Instant TO   = Instant.parse("1995-08-01T00:00:00Z");

    @Test
    void rangeMapsAllDtoFieldsCorrectly() {
        Instant window = Instant.parse("1995-07-10T08:00:00Z");
        when(repo.findRange(any(), any(), any()))
            .thenReturn(List.of(statusMetric("4xx", window, 250L)));

        StatusMetricDto dto = service.range("4xx", FROM, TO).get(0);

        assertThat(dto.statusClass()).isEqualTo("4xx");
        assertThat(dto.windowStart()).isEqualTo(window);
        assertThat(dto.count()).isEqualTo(250L);
    }

    @Test
    void rangeMapsManyEntities() {
        when(repo.findRange(any(), any(), any())).thenReturn(List.of(
            statusMetric("2xx", FROM,                  1_000L),
            statusMetric("2xx", FROM.plusSeconds(600),   900L)
        ));

        assertThat(service.range("2xx", FROM, TO)).hasSize(2);
    }

    @Test
    void rangeReturnsEmptyListWhenNoData() {
        when(repo.findRange(any(), any(), any())).thenReturn(List.of());

        assertThat(service.range("5xx", FROM, TO)).isEmpty();
    }

    @Test
    void rangeDelegatesToRepositoryWithExactArgs() {
        when(repo.findRange("4xx", FROM, TO)).thenReturn(List.of());

        service.range("4xx", FROM, TO);

        verify(repo).findRange("4xx", FROM, TO);
        verifyNoMoreInteractions(repo);
    }

    private StatusMetric statusMetric(String sc, Instant window, long count) {
        StatusMetric m = new StatusMetric();
        m.setStatusClass(sc);
        m.setWindowStart(window);
        m.setCount(count);
        return m;
    }
}
