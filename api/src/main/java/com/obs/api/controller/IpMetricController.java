package com.obs.api.controller;

import com.obs.api.dto.IpMetricDto;
import com.obs.api.exception.BadRequestException;
import com.obs.api.service.IpMetricService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IpMetricController {

    private final IpMetricService service;

    public IpMetricController(IpMetricService service) { this.service = service; }

    @GetMapping("/api/metrics/ip/{ip}")
    public List<IpMetricDto> byIp(
            @PathVariable String ip,
            @RequestParam String from,
            @RequestParam String to) {
        Instant fromInst = parseInstant(from, "from");
        Instant toInst = parseInstant(to, "to");
        if (toInst.isBefore(fromInst)) {
            throw new BadRequestException("to must not be before from");
        }
        return service.range(ip, fromInst, toInst);
    }

    private Instant parseInstant(String value, String name) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("invalid ISO-8601 value for parameter: " + name);
        }
    }
}
