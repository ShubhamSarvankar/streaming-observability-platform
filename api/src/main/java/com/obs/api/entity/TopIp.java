package com.obs.api.entity;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("top_ips_by_window")
public class TopIp {

    @PrimaryKey
    private TopIpKey key;

    public TopIpKey getKey() { return key; }
    public void setKey(TopIpKey key) { this.key = key; }
}
