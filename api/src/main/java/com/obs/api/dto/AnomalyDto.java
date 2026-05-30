package com.obs.api.dto;

import java.time.Instant;

public record AnomalyDto(Instant windowStart, String metric, String subject,
                         long value, long threshold) {}
