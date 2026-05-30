package com.obs.api.dto;

import java.time.Instant;

public record StatusMetricDto(String statusClass, Instant windowStart, long count) {}
