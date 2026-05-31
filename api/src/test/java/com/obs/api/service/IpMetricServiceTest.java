package com.obs.api.service;

import com.obs.api.dto.IpMetricDto;
import com.obs.api.entity.IpMetric;
import com.obs.api.repository.IpMetricRepository;
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
class IpMetricServiceTest {

    @Mock IpMetricRepository repo;
    @InjectMocks IpMetricService service;

    private static final Instant FROM = Instant.parse("1995-07-01T00:00:00Z");
    private static final Instant TO   = Instant.parse("1995-08-01T00:00:00Z");

    @Test
    void rangeMapsAllDtoFieldsCorrectly() {
        Instant window = Instant.parse("1995-07-03T16:20:00Z");
        when(repo.findRange(any(), any(), any()))
            .thenReturn(List.of(ipMetric("199.72.81.55", window, 87L)));

        IpMetricDto dto = service.range("199.72.81.55", FROM, TO).get(0);

        assertThat(dto.clientIp()).isEqualTo("199.72.81.55");
        assertThat(dto.windowStart()).isEqualTo(window);
        assertThat(dto.requestCount()).isEqualTo(87L);
    }

    @Test
    void rangeMapsManyEntities() {
        when(repo.findRange(any(), any(), any())).thenReturn(List.of(
            ipMetric("10.0.0.1", FROM,                   10L),
            ipMetric("10.0.0.1", FROM.plusSeconds(600),  15L)
        ));

        assertThat(service.range("10.0.0.1", FROM, TO)).hasSize(2);
    }

    @Test
    void rangeReturnsEmptyListWhenNoData() {
        when(repo.findRange(any(), any(), any())).thenReturn(List.of());

        assertThat(service.range("1.2.3.4", FROM, TO)).isEmpty();
    }

    @Test
    void rangeDelegatesToRepositoryWithExactArgs() {
        when(repo.findRange("10.0.0.1", FROM, TO)).thenReturn(List.of());

        service.range("10.0.0.1", FROM, TO);

        verify(repo).findRange("10.0.0.1", FROM, TO);
        verifyNoMoreInteractions(repo);
    }

    private IpMetric ipMetric(String ip, Instant window, long count) {
        IpMetric m = new IpMetric();
        m.setClientIp(ip);
        m.setWindowStart(window);
        m.setRequestCount(count);
        return m;
    }
}
