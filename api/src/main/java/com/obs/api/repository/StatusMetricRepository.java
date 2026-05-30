package com.obs.api.repository;

import com.obs.api.entity.StatusMetric;
import java.time.Instant;
import java.util.List;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatusMetricRepository extends CassandraRepository<StatusMetric, String> {

    @Query("SELECT * FROM metrics_by_status WHERE status_class = :sc " +
           "AND window_start >= :from AND window_start <= :to")
    List<StatusMetric> findRange(@Param("sc") String statusClass,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to);
}
