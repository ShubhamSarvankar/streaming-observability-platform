package com.obs.api.entity;

import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

@PrimaryKeyClass
public class TopIpKey implements Serializable {

    @PrimaryKeyColumn(name = "window_start", type = PrimaryKeyType.PARTITIONED)
    private Instant windowStart;

    @PrimaryKeyColumn(name = "request_count", ordinal = 0, type = PrimaryKeyType.CLUSTERED,
                      ordering = Ordering.DESCENDING)
    private long requestCount;

    @PrimaryKeyColumn(name = "client_ip", ordinal = 1, type = PrimaryKeyType.CLUSTERED,
                      ordering = Ordering.ASCENDING)
    private String clientIp;

    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public long getRequestCount() { return requestCount; }
    public void setRequestCount(long requestCount) { this.requestCount = requestCount; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
}
