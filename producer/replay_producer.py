import json
import os
import time
import logging
from confluent_kafka import Producer
from log_schema import parse_line

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("producer")

BOOTSTRAP = os.environ.get("KAFKA_BOOTSTRAP", "kafka:9092")
TOPIC = os.environ.get("KAFKA_TOPIC", "access-logs")
DATASET = os.environ.get("DATASET_PATH", "/data/access_log")
RATE = int(os.environ.get("REPLAY_RATE_PER_SEC", "200"))


def main():
    producer = Producer({"bootstrap.servers": BOOTSTRAP})
    interval = 1.0 / RATE if RATE > 0 else 0.0
    sent = 0
    skipped = 0
    with open(DATASET, "r", encoding="latin-1") as f:
        for line in f:
            event = parse_line(line)
            if event is None:
                skipped += 1
                if skipped % 1000 == 0:
                    log.warning("skipped %d unparseable lines", skipped)
                continue
            producer.produce(
                TOPIC,
                key=event["client_ip"].encode("utf-8"),
                value=json.dumps(event).encode("utf-8"),
            )
            sent += 1
            if sent % 5000 == 0:
                producer.poll(0)
                log.info("sent %d events", sent)
            if interval:
                time.sleep(interval)
    producer.flush()
    log.info("done: sent=%d skipped=%d", sent, skipped)


if __name__ == "__main__":
    main()
