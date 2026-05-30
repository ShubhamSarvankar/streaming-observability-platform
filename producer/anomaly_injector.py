import json
import os
from datetime import datetime, timezone
from confluent_kafka import Producer

BOOTSTRAP = os.environ.get("KAFKA_BOOTSTRAP", "kafka:9092")
TOPIC = os.environ.get("KAFKA_TOPIC", "access-logs")
SPIKE_IP = "10.66.66.66"


def event(ip, path, status):
    return {
        "host": ip, "client_ip": ip,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "method": "GET", "path": path, "protocol": "HTTP/1.0",
        "status": status, "bytes": 0,
    }


def main():
    p = Producer({"bootstrap.servers": BOOTSTRAP})
    for _ in range(1500):
        p.produce(TOPIC, key=SPIKE_IP.encode(),
                  value=json.dumps(event(SPIKE_IP, "/login", 200)).encode())
    for i in range(600):
        ip = f"10.0.0.{i % 250}"
        p.produce(TOPIC, key=ip.encode(),
                  value=json.dumps(event(ip, "/missing", 404)).encode())
    p.flush()
    print("injected anomaly burst")


if __name__ == "__main__":
    main()
