package com.obs.api.repository;

import com.obs.api.entity.TopIp;
import com.obs.api.entity.TopIpKey;
import java.time.Instant;
import java.util.List;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopIpRepository extends CassandraRepository<TopIp, TopIpKey> {

    @Query("SELECT * FROM top_ips_by_window WHERE window_start = :w LIMIT :n")
    List<TopIp> findTop(@Param("w") Instant window, @Param("n") int n);
}
