package com.obs.api.repository;

import com.obs.api.entity.Anomaly;
import com.obs.api.entity.AnomalyKey;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.cassandra.repository.CassandraRepository;

public interface AnomalyRepository extends CassandraRepository<Anomaly, AnomalyKey> {

    List<Anomaly> findByKeyDay(LocalDate day);
}
