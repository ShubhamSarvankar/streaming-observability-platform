package com.obs.api.controller;

import com.obs.api.dto.IpMetricDto;
import com.obs.api.exception.BadRequestException;
import com.obs.api.service.IpMetricService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IpMetricController {

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    private final IpMetricService service;

    public IpMetricController(IpMetricService service) { this.service = service; }

    @GetMapping("/api/metrics/ip/{ip}")
    public List<IpMetricDto> byIp(
            @PathVariable String ip,
            @RequestParam String from,
            @RequestParam String to) {
        if (!IPV4.matcher(ip).matches()) {
            throw new BadRequestException("ip must be a valid IPv4 address");
        }
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
