package com.obs.api.controller;

import com.obs.api.dto.PathMetricDto;
import com.obs.api.exception.BadRequestException;
import com.obs.api.service.PathMetricService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PathMetricController {

    private final PathMetricService service;

    public PathMetricController(PathMetricService service) { this.service = service; }

    @GetMapping("/api/metrics/path")
    public List<PathMetricDto> byPath(
            @RequestParam String path,
            @RequestParam String from,
            @RequestParam String to) {
        if (path.isBlank()) {
            throw new BadRequestException("path must not be blank");
        }
        Instant fromInst = parseInstant(from, "from");
        Instant toInst = parseInstant(to, "to");
        if (toInst.isBefore(fromInst)) {
            throw new BadRequestException("to must not be before from");
        }
        return service.range(path, fromInst, toInst);
    }

    private Instant parseInstant(String value, String name) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("invalid ISO-8601 value for parameter: " + name);
        }
    }
}
