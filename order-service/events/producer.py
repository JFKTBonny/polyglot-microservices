import json
import os
from aiokafka import AIOKafkaProducer
from dotenv import load_dotenv

load_dotenv()

KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")

# Global producer instance — created once on startup
_producer = None

async def get_producer() -> AIOKafkaProducer:
    global _producer
    if _producer is None:
        _producer = AIOKafkaProducer(
            bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
            # Serialize values as JSON bytes automatically
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            # key serializer — used for partition routing
            key_serializer=lambda k: k.encode("utf-8") if k else None,
            # Wait for all replicas to acknowledge (safer)
            acks="all"
        )
        await _producer.start()
        print("[kafka] producer connected")
    return _producer

async def publish(topic: str, key: str, payload: dict):
    """
    Publish a single event to a Kafka topic.

    topic   — the topic name e.g. 'order.created'
    key     — used for partition routing, use user_id so
              same user's events always go to same partition
    payload — the event data as a dict, serialized to JSON automatically
    """
    producer = await get_producer()
    await producer.send_and_wait(
        topic,
        key=key,
        value=payload
    )
    print(f"[kafka] published to '{topic}': {payload}")

async def close_producer():
    global _producer
    if _producer is not None:
        await _producer.stop()
        _producer = None
        print("[kafka] producer closed")