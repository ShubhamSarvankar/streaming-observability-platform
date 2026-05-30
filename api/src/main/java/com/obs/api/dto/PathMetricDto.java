package com.obs.api.dto;

import java.time.Instant;

public record PathMetricDto(String path, Instant windowStart, long hitCount, long byteTotal) {}
