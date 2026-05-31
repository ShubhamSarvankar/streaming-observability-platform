package com.obs.api.controller;

import com.obs.api.dto.IpMetricDto;
import com.obs.api.service.IpMetricService;
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

@WebMvcTest(IpMetricController.class)
class IpMetricControllerTest {

    @Autowired MockMvc mvc;
    @MockBean  IpMetricService service;

    private static final String IP   = "199.72.81.55";
    private static final String FROM = "1995-07-01T00:00:00Z";
    private static final String TO   = "1995-08-01T00:00:00Z";

    @Test
    void returns200WithMetricsForValidRequest() throws Exception {
        Instant window = Instant.parse(FROM);
        IpMetricDto dto = new IpMetricDto(IP, window, 87L);
        when(service.range(eq(IP), any(), any())).thenReturn(List.of(dto));

        mvc.perform(get("/api/metrics/ip/{ip}", IP)
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].clientIp").value(IP))
           .andExpect(jsonPath("$[0].requestCount").value(87));
    }

    @Test
    void returns200WithEmptyArrayWhenNoData() throws Exception {
        when(service.range(any(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/metrics/ip/{ip}", IP)
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-ip", "hostname.example.com", "1.2.3"})
    void returns400WhenIpIsNotValidIpv4(String badIp) throws Exception {
        mvc.perform(get("/api/metrics/ip/{ip}", badIp)
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("ip must be a valid IPv4 address"));
    }

    @Test
    void returns400WhenFromIsNotIso8601() throws Exception {
        mvc.perform(get("/api/metrics/ip/{ip}", IP)
                .param("from", "yesterday")
                .param("to",   TO))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("invalid ISO-8601 value for parameter: from"));
    }

    @Test
    void returns400WhenToIsNotIso8601() throws Exception {
        mvc.perform(get("/api/metrics/ip/{ip}", IP)
                .param("from", FROM)
                .param("to",   "tomorrow"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("invalid ISO-8601 value for parameter: to"));
    }

    @Test
    void returns400WhenToIsBeforeFrom() throws Exception {
        mvc.perform(get("/api/metrics/ip/{ip}", IP)
                .param("from", TO)
                .param("to",   FROM))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("to must not be before from"));
    }

    @Test
    void returns400WhenFromParamMissing() throws Exception {
        mvc.perform(get("/api/metrics/ip/{ip}", IP)
                .param("to", TO))
           .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenToParamMissing() throws Exception {
        mvc.perform(get("/api/metrics/ip/{ip}", IP)
                .param("from", FROM))
           .andExpect(status().isBadRequest());
    }
}
