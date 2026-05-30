package com.obs.api.entity;

import java.time.Instant;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;

@Table("metrics_by_path")
public class PathMetric {

    @PrimaryKeyColumn(name = "path", type = PrimaryKeyType.PARTITIONED)
    private String path;

    @PrimaryKeyColumn(name = "window_start", ordinal = 0, type = PrimaryKeyType.CLUSTERED,
                      ordering = org.springframework.data.cassandra.core.cql.Ordering.DESCENDING)
    private Instant windowStart;

    @Column("hit_count")
    private long hitCount;

    @Column("byte_total")
    private long byteTotal;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public long getHitCount() { return hitCount; }
    public void setHitCount(long hitCount) { this.hitCount = hitCount; }
    public long getByteTotal() { return byteTotal; }
    public void setByteTotal(long byteTotal) { this.byteTotal = byteTotal; }
}
