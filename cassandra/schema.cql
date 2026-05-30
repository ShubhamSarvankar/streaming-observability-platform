CREATE KEYSPACE IF NOT EXISTS obs
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE obs;

CREATE TABLE IF NOT EXISTS metrics_by_path (
    path        text,
    window_start timestamp,
    hit_count   bigint,
    byte_total  bigint,
    PRIMARY KEY ((path), window_start)
) WITH CLUSTERING ORDER BY (window_start DESC);

CREATE TABLE IF NOT EXISTS metrics_by_ip (
    client_ip     text,
    window_start  timestamp,
    request_count bigint,
    PRIMARY KEY ((client_ip), window_start)
) WITH CLUSTERING ORDER BY (window_start DESC);

CREATE TABLE IF NOT EXISTS metrics_by_status (
    status_class text,
    window_start timestamp,
    count        bigint,
    PRIMARY KEY ((status_class), window_start)
) WITH CLUSTERING ORDER BY (window_start DESC);

CREATE TABLE IF NOT EXISTS top_ips_by_window (
    window_start  timestamp,
    request_count bigint,
    client_ip     text,
    PRIMARY KEY ((window_start), request_count, client_ip)
) WITH CLUSTERING ORDER BY (request_count DESC, client_ip ASC);

CREATE TABLE IF NOT EXISTS anomalies (
    day          date,
    window_start timestamp,
    metric       text,
    subject      text,
    value        bigint,
    threshold    bigint,
    PRIMARY KEY ((day), window_start, metric, subject)
) WITH CLUSTERING ORDER BY (window_start DESC, metric ASC, subject ASC);
