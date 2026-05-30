package com.obs.api.repository;

import com.obs.api.entity.PathMetric;
import java.time.Instant;
import java.util.List;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PathMetricRepository extends CassandraRepository<PathMetric, String> {

    @Query("SELECT * FROM metrics_by_path WHERE path = :path " +
           "AND window_start >= :from AND window_start <= :to")
    List<PathMetric> findRange(@Param("path") String path,
                               @Param("from") Instant from,
                               @Param("to") Instant to);
}
