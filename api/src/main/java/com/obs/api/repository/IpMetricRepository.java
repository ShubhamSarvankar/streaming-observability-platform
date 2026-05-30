package com.obs.api.repository;

import com.obs.api.entity.IpMetric;
import java.time.Instant;
import java.util.List;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IpMetricRepository extends CassandraRepository<IpMetric, String> {

    @Query("SELECT * FROM metrics_by_ip WHERE client_ip = :ip " +
           "AND window_start >= :from AND window_start <= :to")
    List<IpMetric> findRange(@Param("ip") String ip,
                             @Param("from") Instant from,
                             @Param("to") Instant to);
}
