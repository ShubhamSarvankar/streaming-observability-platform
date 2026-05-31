package com.obs.api.controller;

import com.obs.api.dto.AnomalyDto;
import com.obs.api.service.AnomalyService;
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

@WebMvcTest(AnomalyController.class)
class AnomalyControllerTest {

    @Autowired MockMvc mvc;
    @MockBean  AnomalyService service;

    @Test
    void returns200WithAnomaliesForValidDay() throws Exception {
        Instant window = Instant.parse("1995-07-03T16:20:00Z");
        AnomalyDto dto = new AnomalyDto(window, "ip_volume", "10.66.66.66", 1_500L, 500L);
        when(service.forDay(any())).thenReturn(List.of(dto));

        mvc.perform(get("/api/anomalies").param("day", "1995-07-03"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].metric").value("ip_volume"))
           .andExpect(jsonPath("$[0].subject").value("10.66.66.66"))
           .andExpect(jsonPath("$[0].value").value(1_500))
           .andExpect(jsonPath("$[0].threshold").value(500));
    }

    @Test
    void returns200WithMultipleAnomalies() throws Exception {
        Instant window = Instant.parse("1995-07-03T16:20:00Z");
        List<AnomalyDto> dtos = List.of(
            new AnomalyDto(window, "ip_volume",    "10.66.66.66", 1_500L, 500L),
            new AnomalyDto(window, "status_class", "4xx",          600L,  200L)
        );
        when(service.forDay(any())).thenReturn(dtos);

        mvc.perform(get("/api/anomalies").param("day", "1995-07-03"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(2))
           .andExpect(jsonPath("$[1].metric").value("status_class"));
    }

    @Test
    void returns200WithEmptyArrayWhenNoAnomalies() throws Exception {
        when(service.forDay(any())).thenReturn(List.of());

        mvc.perform(get("/api/anomalies").param("day", "1995-07-03"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returns400WhenDayUsesWrongFormat() throws Exception {
        mvc.perform(get("/api/anomalies").param("day", "07-03-1995"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message")
               .value("invalid date for parameter: day (expected YYYY-MM-DD)"));
    }

    @Test
    void returns400WhenDayIsNotADate() throws Exception {
        mvc.perform(get("/api/anomalies").param("day", "not-a-date"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message")
               .value("invalid date for parameter: day (expected YYYY-MM-DD)"));
    }

    @Test
    void returns400WhenDayParamMissing() throws Exception {
        mvc.perform(get("/api/anomalies"))
           .andExpect(status().isBadRequest());
    }
}
