package com.obs.api.dto;

import java.time.Instant;

public record IpMetricDto(String clientIp, Instant windowStart, long requestCount) {}
