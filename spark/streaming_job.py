import os
from pyspark.sql import SparkSession, functions as F
from pyspark.sql.types import (
    StructType, StructField, StringType, IntegerType, LongType
)

BOOTSTRAP = os.environ.get("KAFKA_BOOTSTRAP", "kafka:9092")
TOPIC = os.environ.get("KAFKA_TOPIC", "access-logs")
KEYSPACE = os.environ.get("CASSANDRA_KEYSPACE", "obs")
WINDOW_MIN = int(os.environ.get("WINDOW_MINUTES", "10"))
WATERMARK_MIN = int(os.environ.get("WATERMARK_MINUTES", "5"))
CHECKPOINT = os.environ.get("CHECKPOINT_DIR", "/checkpoints")
IP_FLOOR = int(os.environ.get("ANOMALY_IP_FLOOR", "500"))
STATUS_FLOOR = int(os.environ.get("ANOMALY_STATUS_FLOOR", "200"))

SCHEMA = StructType([
    StructField("host", StringType()),
    StructField("client_ip", StringType()),
    StructField("timestamp", StringType()),
    StructField("method", StringType()),
    StructField("path", StringType()),
    StructField("protocol", StringType()),
    StructField("status", IntegerType()),
    StructField("bytes", LongType()),
])

spark = (
    SparkSession.builder.appName("obs-streaming")
    .config("spark.cassandra.connection.host",
            os.environ.get("CASSANDRA_CONTACT_POINTS", "cassandra"))
    .config("spark.cassandra.connection.port",
            os.environ.get("CASSANDRA_PORT", "9042"))
    .config("spark.sql.extensions",
            "com.datastax.spark.connector.CassandraSparkExtensions")
    .config("spark.sql.session.timeZone", "UTC")
    .getOrCreate()
)
spark.sparkContext.setLogLevel("WARN")

raw = (
    spark.readStream.format("kafka")
    .option("kafka.bootstrap.servers", BOOTSTRAP)
    .option("subscribe", TOPIC)
    .option("startingOffsets", "earliest")
    .load()
)

events = (
    raw.select(F.from_json(F.col("value").cast("string"), SCHEMA).alias("e"))
    .select("e.*")
    .withColumn("event_time", F.to_timestamp("timestamp"))
    .withColumn("status_class", F.concat((F.col("status") / 100).cast("int"),
                                          F.lit("xx")))
    .withWatermark("event_time", f"{WATERMARK_MIN} minutes")
)

win = F.window("event_time", f"{WINDOW_MIN} minutes")


def write_cass(df, table, columns):
    (df.select(*columns)
        .write.format("org.apache.spark.sql.cassandra")
        .options(keyspace=KEYSPACE, table=table)
        .mode("append").save())


def by_path_batch(df, _):
    agg = (df.groupBy(win, "path")
             .agg(F.count("*").alias("hit_count"),
                  F.sum("bytes").alias("byte_total"))
             .withColumn("window_start", F.col("window.start")))
    write_cass(agg, "metrics_by_path",
               ["path", "window_start", "hit_count", "byte_total"])


def by_ip_batch(df, _):
    agg = (df.groupBy(win, "client_ip")
             .agg(F.count("*").alias("request_count"))
             .withColumn("window_start", F.col("window.start")))
    write_cass(agg, "metrics_by_ip",
               ["client_ip", "window_start", "request_count"])
    write_cass(agg, "top_ips_by_window",
               ["window_start", "request_count", "client_ip"])
    spikes = agg.filter(F.col("request_count") >= IP_FLOOR)
    if not spikes.rdd.isEmpty():
        flagged = (spikes
            .withColumn("day", F.to_date("window_start"))
            .withColumn("metric", F.lit("ip_request_spike"))
            .withColumnRenamed("client_ip", "subject")
            .withColumnRenamed("request_count", "value")
            .withColumn("threshold", F.lit(IP_FLOOR).cast("long")))
        write_cass(flagged, "anomalies",
                   ["day", "window_start", "metric", "subject",
                    "value", "threshold"])


def by_status_batch(df, _):
    agg = (df.groupBy(win, "status_class")
             .agg(F.count("*").alias("count"))
             .withColumn("window_start", F.col("window.start")))
    write_cass(agg, "metrics_by_status",
               ["status_class", "window_start", "count"])
    spikes = agg.filter(
        (F.col("status_class").isin("4xx", "5xx")) &
        (F.col("count") >= STATUS_FLOOR))
    if not spikes.rdd.isEmpty():
        flagged = (spikes
            .withColumn("day", F.to_date("window_start"))
            .withColumn("metric",
                        F.concat(F.lit("status_"),
                                 F.col("status_class"), F.lit("_spike")))
            .withColumnRenamed("status_class", "subject")
            .withColumnRenamed("count", "value")
            .withColumn("threshold", F.lit(STATUS_FLOOR).cast("long")))
        write_cass(flagged, "anomalies",
                   ["day", "window_start", "metric", "subject",
                    "value", "threshold"])


def start(name, fn):
    return (events.writeStream.outputMode("update")
            .foreachBatch(fn)
            .option("checkpointLocation", f"{CHECKPOINT}/{name}")
            .start())


q1 = start("by_path", by_path_batch)
q2 = start("by_ip", by_ip_batch)
q3 = start("by_status", by_status_batch)
spark.streams.awaitAnyTermination()
