package com.obs.api.controller;

import com.obs.api.dto.TopIpDto;
import com.obs.api.exception.BadRequestException;
import com.obs.api.service.TopIpService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TopIpController {

    private final TopIpService service;

    public TopIpController(TopIpService service) { this.service = service; }

    @GetMapping("/api/metrics/top-ips")
    public List<TopIpDto> topIps(
            @RequestParam String window,
            @RequestParam(defaultValue = "10") int n) {
        Instant windowInst;
        try {
            windowInst = Instant.parse(window);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("invalid ISO-8601 value for parameter: window");
        }
        if (n < 1 || n > 100) {
            throw new BadRequestException("n must be between 1 and 100");
        }
        return service.top(windowInst, n);
    }
}
