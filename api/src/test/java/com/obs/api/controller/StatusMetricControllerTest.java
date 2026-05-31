package com.obs.api.controller;

import com.obs.api.dto.StatusMetricDto;
import com.obs.api.service.StatusMetricService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

@WebMvcTest(StatusMetricController.class)
class StatusMetricControllerTest {

    @Autowired MockMvc mvc;
    @MockBean  StatusMetricService service;

    private static final String FROM = "1995-07-01T00:00:00Z";
    private static final String TO   = "1995-08-01T00:00:00Z";

    @ParameterizedTest
    @ValueSource(strings = {"1xx", "2xx", "3xx", "4xx", "5xx"})
    void returns200ForEachValidStatusClass(String sc) throws Exception {
        Instant window = Instant.parse(FROM);
        StatusMetricDto dto = new StatusMetricDto(sc, window, 500L);
        when(service.range(eq(sc), any(), any())).thenReturn(List.of(dto));

        mvc.perform(get("/api/metrics/status/{sc}", sc)
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].statusClass").value(sc))
           .andExpect(jsonPath("$[0].count").value(500));
    }

    @Test
    void returns200WithEmptyArrayWhenNoData() throws Exception {
        when(service.range(any(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/metrics/status/{sc}", "4xx")
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "6xx", "200", "ok", "error"})
    void returns400WhenStatusClassIsInvalid(String bad) throws Exception {
        mvc.perform(get("/api/metrics/status/{sc}", bad)
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("statusClass must be one of: 1xx 2xx 3xx 4xx 5xx"));
    }

    @Test
    void returns400WhenFromIsNotIso8601() throws Exception {
        mvc.perform(get("/api/metrics/status/{sc}", "4xx")
                .param("from", "not-a-timestamp")
                .param("to",   TO))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("invalid ISO-8601 value for parameter: from"));
    }

    @Test
    void returns400WhenToIsNotIso8601() throws Exception {
        mvc.perform(get("/api/metrics/status/{sc}", "4xx")
                .param("from", FROM)
                .param("to",   "not-a-timestamp"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("invalid ISO-8601 value for parameter: to"));
    }

    @Test
    void returns400WhenToIsBeforeFrom() throws Exception {
        mvc.perform(get("/api/metrics/status/{sc}", "4xx")
                .param("from", TO)
                .param("to",   FROM))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("to must not be before from"));
    }

    @Test
    void returns400WhenFromParamMissing() throws Exception {
        mvc.perform(get("/api/metrics/status/{sc}", "4xx")
                .param("to", TO))
           .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenToParamMissing() throws Exception {
        mvc.perform(get("/api/metrics/status/{sc}", "4xx")
                .param("from", FROM))
           .andExpect(status().isBadRequest());
    }
}
