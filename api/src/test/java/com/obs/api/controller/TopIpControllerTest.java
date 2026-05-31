package com.obs.api.controller;

import com.obs.api.dto.TopIpDto;
import com.obs.api.service.TopIpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TopIpController.class)
class TopIpControllerTest {

    @Autowired MockMvc mvc;
    @MockBean  TopIpService service;

    private static final String WINDOW = "1995-07-03T16:20:00Z";

    @Test
    void returns200WithDefaultNOfTen() throws Exception {
        TopIpDto dto = new TopIpDto("199.72.81.55", 1_200L);
        when(service.top(any(), eq(10))).thenReturn(List.of(dto));

        mvc.perform(get("/api/metrics/top-ips").param("window", WINDOW))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].clientIp").value("199.72.81.55"))
           .andExpect(jsonPath("$[0].requestCount").value(1_200));
    }

    @Test
    void returns200WithCustomN() throws Exception {
        List<TopIpDto> dtos = List.of(
            new TopIpDto("10.0.0.1", 900L),
            new TopIpDto("10.0.0.2", 600L),
            new TopIpDto("10.0.0.3", 300L)
        );
        when(service.top(any(), eq(3))).thenReturn(dtos);

        mvc.perform(get("/api/metrics/top-ips")
                .param("window", WINDOW)
                .param("n", "3"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(3))
           .andExpect(jsonPath("$[0].requestCount").value(900))
           .andExpect(jsonPath("$[2].requestCount").value(300));
    }

    @Test
    void returns200WithEmptyArrayWhenWindowHasNoData() throws Exception {
        when(service.top(any(), anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/metrics/top-ips").param("window", WINDOW))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returns400WhenWindowIsNotIso8601() throws Exception {
        mvc.perform(get("/api/metrics/top-ips").param("window", "yesterday"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("invalid ISO-8601 value for parameter: window"));
    }

    @Test
    void returns400WhenNIsZero() throws Exception {
        mvc.perform(get("/api/metrics/top-ips")
                .param("window", WINDOW)
                .param("n", "0"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("n must be between 1 and 100"));
    }

    @Test
    void returns400WhenNIsNegative() throws Exception {
        mvc.perform(get("/api/metrics/top-ips")
                .param("window", WINDOW)
                .param("n", "-1"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("n must be between 1 and 100"));
    }

    @Test
    void returns400WhenNExceeds100() throws Exception {
        mvc.perform(get("/api/metrics/top-ips")
                .param("window", WINDOW)
                .param("n", "101"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value("n must be between 1 and 100"));
    }

    @Test
    void returns400WhenWindowParamMissing() throws Exception {
        mvc.perform(get("/api/metrics/top-ips"))
           .andExpect(status().isBadRequest());
    }
}
