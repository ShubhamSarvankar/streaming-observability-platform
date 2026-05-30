package com.obs.api.entity;

import java.time.Instant;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;

@Table("metrics_by_status")
public class StatusMetric {

    @PrimaryKeyColumn(name = "status_class", type = PrimaryKeyType.PARTITIONED)
    private String statusClass;

    @PrimaryKeyColumn(name = "window_start", ordinal = 0, type = PrimaryKeyType.CLUSTERED,
                      ordering = org.springframework.data.cassandra.core.cql.Ordering.DESCENDING)
    private Instant windowStart;

    @Column("count")
    private long count;

    public String getStatusClass() { return statusClass; }
    public void setStatusClass(String statusClass) { this.statusClass = statusClass; }
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
