package com.obs.api.service;

import com.obs.api.dto.TopIpDto;
import com.obs.api.entity.TopIp;
import com.obs.api.entity.TopIpKey;
import com.obs.api.repository.TopIpRepository;
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
class TopIpServiceTest {

    @Mock TopIpRepository repo;
    @InjectMocks TopIpService service;

    private static final Instant WINDOW = Instant.parse("1995-07-03T16:20:00Z");

    @Test
    void topMapsDtoFieldsCorrectly() {
        when(repo.findTop(any(), anyInt()))
            .thenReturn(List.of(buildTopIp(WINDOW, "199.72.81.55", 1_200L)));

        TopIpDto dto = service.top(WINDOW, 1).get(0);

        assertThat(dto.clientIp()).isEqualTo("199.72.81.55");
        assertThat(dto.requestCount()).isEqualTo(1_200L);
    }

    @Test
    void topPreservesOrderFromRepository() {
        List<TopIp> entities = List.of(
            buildTopIp(WINDOW, "10.0.0.1", 900L),
            buildTopIp(WINDOW, "10.0.0.2", 600L),
            buildTopIp(WINDOW, "10.0.0.3", 300L)
        );
        when(repo.findTop(eq(WINDOW), eq(3))).thenReturn(entities);

        List<TopIpDto> result = service.top(WINDOW, 3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).requestCount()).isEqualTo(900L);
        assertThat(result.get(1).requestCount()).isEqualTo(600L);
        assertThat(result.get(2).requestCount()).isEqualTo(300L);
    }

    @Test
    void topReturnsEmptyListWhenWindowHasNoData() {
        when(repo.findTop(any(), anyInt())).thenReturn(List.of());

        assertThat(service.top(WINDOW, 10)).isEmpty();
    }

    @Test
    void topDelegatesToRepositoryWithExactArgs() {
        when(repo.findTop(WINDOW, 5)).thenReturn(List.of());

        service.top(WINDOW, 5);

        verify(repo).findTop(WINDOW, 5);
        verifyNoMoreInteractions(repo);
    }

    private TopIp buildTopIp(Instant window, String ip, long count) {
        TopIpKey key = new TopIpKey();
        key.setWindowStart(window);
        key.setClientIp(ip);
        key.setRequestCount(count);
        TopIp t = new TopIp();
        t.setKey(key);
        return t;
    }
}
