package com.obs.api.controller;

import com.obs.api.dto.AnomalyDto;
import com.obs.api.exception.BadRequestException;
import com.obs.api.service.AnomalyService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnomalyController {

    private final AnomalyService service;

    public AnomalyController(AnomalyService service) { this.service = service; }

    @GetMapping("/api/anomalies")
    public List<AnomalyDto> anomalies(@RequestParam String day) {
        LocalDate date;
        try {
            date = LocalDate.parse(day);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("invalid date for parameter: day (expected YYYY-MM-DD)");
        }
        return service.forDay(date);
    }
}
