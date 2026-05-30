package com.obs.api.entity;

import java.time.Instant;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;

@Table("metrics_by_ip")
public class IpMetric {

    @PrimaryKeyColumn(name = "client_ip", type = PrimaryKeyType.PARTITIONED)
    private String clientIp;

    @PrimaryKeyColumn(name = "window_start", ordinal = 0, type = PrimaryKeyType.CLUSTERED,
                      ordering = org.springframework.data.cassandra.core.cql.Ordering.DESCENDING)
    private Instant windowStart;

    @Column("request_count")
    private long requestCount;

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public long getRequestCount() { return requestCount; }
    public void setRequestCount(long requestCount) { this.requestCount = requestCount; }
}
