package com.obs.api.entity;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

@PrimaryKeyClass
public class AnomalyKey implements Serializable {

    @PrimaryKeyColumn(name = "day", type = PrimaryKeyType.PARTITIONED)
    private LocalDate day;

    @PrimaryKeyColumn(name = "window_start", ordinal = 0, type = PrimaryKeyType.CLUSTERED,
                      ordering = Ordering.DESCENDING)
    private Instant windowStart;

    @PrimaryKeyColumn(name = "metric", ordinal = 1, type = PrimaryKeyType.CLUSTERED,
                      ordering = Ordering.ASCENDING)
    private String metric;

    @PrimaryKeyColumn(name = "subject", ordinal = 2, type = PrimaryKeyType.CLUSTERED,
                      ordering = Ordering.ASCENDING)
    private String subject;

    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
}
