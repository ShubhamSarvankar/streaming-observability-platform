package com.obs.api.controller;

import com.obs.api.dto.PathMetricDto;
import com.obs.api.service.PathMetricService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PathMetricController.class)
class PathMetricControllerTest {

    @Autowired MockMvc mvc;
    @MockBean  PathMetricService service;

    private static final String FROM = "1995-07-01T00:00:00Z";
    private static final String TO   = "1995-08-01T00:00:00Z";

    @Test
    void returns200WithDataForValidRequest() throws Exception {
        Instant window = Instant.parse(FROM);
        PathMetricDto dto = new PathMetricDto("/index.html", window, 42L, 128_000L);
        when(service.range(eq("/index.html"), any(), any())).thenReturn(List.of(dto));

        mvc.perform(get("/api/metrics/path")
                .param("path", "/index.html")
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$").isArray())
           .andExpect(jsonPath("$[0].path").value("/index.html"))
           .andExpect(jsonPath("$[0].hitCount").value(42))
           .andExpect(jsonPath("$[0].byteTotal").value(128_000));
    }

    @Test
    void returns200WithEmptyArrayWhenNoData() throws Exception {
        when(service.range(any(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/metrics/path")
                .param("path", "/no-such/path/")
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$").isArray())
           .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returns400WhenPathIsBlank() throws Exception {
        mvc.perform(get("/api/metrics/path")
                .param("path", "   ")
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.status").value(400))
           .andExpect(jsonPath("$.message").value("path must not be blank"));
    }

    @Test
    void returns400WhenFromIsNotIso8601() throws Exception {
        mvc.perform(get("/api/metrics/path")
                .param("path", "/index.html")
                .param("from", "not-a-date")
                .param("to",   TO))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("invalid ISO-8601 value for parameter: from"));
    }

    @Test
    void returns400WhenToIsNotIso8601() throws Exception {
        mvc.perform(get("/api/metrics/path")
                .param("path", "/index.html")
                .param("from", FROM)
                .param("to",   "not-a-date"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("invalid ISO-8601 value for parameter: to"));
    }

    @Test
    void returns400WhenToIsBeforeFrom() throws Exception {
        mvc.perform(get("/api/metrics/path")
                .param("path", "/index.html")
                .param("from", TO)
                .param("to",   FROM))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("to must not be before from"));
    }

    @Test
    void returns400WhenPathParamMissing() throws Exception {
        mvc.perform(get("/api/metrics/path")
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenFromParamMissing() throws Exception {
        mvc.perform(get("/api/metrics/path")
                .param("path", "/index.html")
                .param("to",   TO))
           .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenToParamMissing() throws Exception {
        mvc.perform(get("/api/metrics/path")
                .param("path", "/index.html")
                .param("from", FROM))
           .andExpect(status().isBadRequest());
    }
}
