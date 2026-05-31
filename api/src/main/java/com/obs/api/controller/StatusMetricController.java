package com.obs.api.controller;

import com.obs.api.dto.StatusMetricDto;
import com.obs.api.exception.BadRequestException;
import com.obs.api.service.StatusMetricService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusMetricController {

    private static final Set<String> VALID_CLASSES = Set.of("1xx", "2xx", "3xx", "4xx", "5xx");

    private final StatusMetricService service;

    public StatusMetricController(StatusMetricService service) { this.service = service; }

    @GetMapping("/api/metrics/status/{statusClass}")
    public List<StatusMetricDto> byStatus(
            @PathVariable String statusClass,
            @RequestParam String from,
            @RequestParam String to) {
        if (!VALID_CLASSES.contains(statusClass)) {
            throw new BadRequestException("statusClass must be one of: 1xx 2xx 3xx 4xx 5xx");
        }
        Instant fromInst = parseInstant(from, "from");
        Instant toInst = parseInstant(to, "to");
        if (toInst.isBefore(fromInst)) {
            throw new BadRequestException("to must not be before from");
        }
        return service.range(statusClass, fromInst, toInst);
    }

    private Instant parseInstant(String value, String name) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("invalid ISO-8601 value for parameter: " + name);
        }
    }
}
