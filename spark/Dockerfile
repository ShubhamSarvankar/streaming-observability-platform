FROM apache/spark:3.5.3

USER root

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY streaming_job.py .

CMD ["/opt/spark/bin/spark-submit", \
     "--packages", \
     "com.datastax.spark:spark-cassandra-connector_2.12:3.5.1,org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.3", \
     "--master", "local[*]", \
     "/app/streaming_job.py"]
