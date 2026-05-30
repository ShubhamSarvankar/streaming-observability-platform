package com.obs.api.entity;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("anomalies")
public class Anomaly {

    @PrimaryKey
    private AnomalyKey key;

    @Column("value")
    private long value;

    @Column("threshold")
    private long threshold;

    public AnomalyKey getKey() { return key; }
    public void setKey(AnomalyKey key) { this.key = key; }
    public long getValue() { return value; }
    public void setValue(long value) { this.value = value; }
    public long getThreshold() { return threshold; }
    public void setThreshold(long threshold) { this.threshold = threshold; }
}
