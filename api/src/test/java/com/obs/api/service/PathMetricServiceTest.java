package com.obs.api.service;

import com.obs.api.dto.PathMetricDto;
import com.obs.api.entity.PathMetric;
import com.obs.api.repository.PathMetricRepository;
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
class PathMetricServiceTest {

    @Mock PathMetricRepository repo;
    @InjectMocks PathMetricService service;

    private static final Instant FROM = Instant.parse("1995-07-01T00:00:00Z");
    private static final Instant TO   = Instant.parse("1995-08-01T00:00:00Z");

    @Test
    void rangeMapsAllDtoFieldsCorrectly() {
        Instant window = Instant.parse("1995-07-03T12:00:00Z");
        when(repo.findRange(any(), any(), any()))
            .thenReturn(List.of(metric("/missions/", window, 42L, 98_000L)));

        PathMetricDto dto = service.range("/missions/", FROM, TO).get(0);

        assertThat(dto.path()).isEqualTo("/missions/");
        assertThat(dto.windowStart()).isEqualTo(window);
        assertThat(dto.hitCount()).isEqualTo(42L);
        assertThat(dto.byteTotal()).isEqualTo(98_000L);
    }

    @Test
    void rangeMapsManyEntitiesToDtos() {
        when(repo.findRange(any(), any(), any())).thenReturn(List.of(
            metric("/index.html", FROM,                       10L, 1_024L),
            metric("/index.html", FROM.plusSeconds(600),      8L,   800L),
            metric("/index.html", FROM.plusSeconds(1_200),   12L, 1_200L)
        ));

        assertThat(service.range("/index.html", FROM, TO)).hasSize(3);
    }

    @Test
    void rangeReturnsEmptyListWhenRepositoryReturnsEmpty() {
        when(repo.findRange(any(), any(), any())).thenReturn(List.of());

        assertThat(service.range("/no-such/", FROM, TO)).isEmpty();
    }

    @Test
    void rangeDelegatesToRepositoryWithExactArgs() {
        when(repo.findRange("/path/", FROM, TO)).thenReturn(List.of());

        service.range("/path/", FROM, TO);

        verify(repo).findRange("/path/", FROM, TO);
        verifyNoMoreInteractions(repo);
    }

    private PathMetric metric(String path, Instant window, long hits, long bytes) {
        PathMetric m = new PathMetric();
        m.setPath(path);
        m.setWindowStart(window);
        m.setHitCount(hits);
        m.setByteTotal(bytes);
        return m;
    }
}
