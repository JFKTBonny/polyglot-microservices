import json
import os
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer 
from aiokafka.errors import KafkaConnectionError
from dotenv import load_dotenv
import asyncio

load_dotenv()

KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
KAFKA_GROUP_ID          = os.getenv("KAFKA_GROUP_ID", "notification-service")
DLQ_TOPIC               = "order.created.dlq"

async def send_order_confirmation(event: dict):
    """
    Simulate sending an order confirmation email.
    In production this would call SendGrid, SES, etc.
    """
    print(f"[email] sending order confirmation to user {event['user_id']}")
    print(f"[email] order {event['order_id']} — total ${event['total']}")
    print(f"[email] items: {event['items']}")
    print(f"[email] ✓ email sent successfully")

async def publish_to_dlq(producer, message, error: Exception):
    """
    Dead Letter Queue — if processing fails 3 times,
    send the message here so it's not lost and can be
    inspected and replayed later.
    """
    
    dlq_payload = {
        "original_topic": "order.created",
        "original_value": message.value,
        "error":          str(error),
    }
    await producer.send_and_wait(
        DLQ_TOPIC,
        value=json.dumps(dlq_payload).encode("utf-8")
    )
    print(f"[dlq] message sent to {DLQ_TOPIC}: {dlq_payload}")

async def start_consumer():
    """
    Main consumer loop — runs forever, processing events as they arrive.
    """
    # Wait for Kafka to fully initialise before connecting
    print("[kafka] waiting 15s for Kafka to fully initialise...")
    await asyncio.sleep(15)

    consumer = AIOKafkaConsumer(
        "order.created",
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        group_id=KAFKA_GROUP_ID,
        # Start from the earliest unprocessed message
        # so we don't miss events if the service restarts
        auto_offset_reset="earliest",
        # Deserialize JSON bytes back to string automatically
        value_deserializer=lambda v: json.loads(v.decode("utf-8")),
        # Don't commit offset automatically — we commit manually
        # after successful processing so we never lose a message
        enable_auto_commit=False
    )

    # DLQ producer — only used when processing fails
    dlq_producer = AIOKafkaProducer(
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS
    )

    await consumer.start()
    await dlq_producer.start()
    print(f"[kafka] consumer started — listening on 'order.created'")
    print(f"[kafka] group_id: {KAFKA_GROUP_ID}")

    try:
        async for message in consumer:
            print(f"[kafka] received message on partition {message.partition} offset {message.offset}")

            event = message.value
            retries = 0
            max_retries = 3

            # Retry loop — attempt processing up to 3 times
            while retries < max_retries:
                try:
                    await send_order_confirmation(event)

                    # Manually commit offset only after successful processing
                    # This guarantees at-least-once delivery
                    await consumer.commit()
                    print(f"[kafka] offset committed for partition {message.partition}")
                    break  # success — exit retry loop

                except Exception as err:
                    retries += 1
                    print(f"[kafka] processing failed (attempt {retries}/{max_retries}): {err}")

                    if retries == max_retries:
                        # All retries exhausted — send to DLQ
                        print(f"[kafka] max retries reached — sending to DLQ")
                        await publish_to_dlq(dlq_producer, message, err)
                        await consumer.commit()  # commit so we don't reprocess forever

                    else:
                        # Wait before retrying — exponential backoff
                        wait = 2 ** retries
                        print(f"[kafka] retrying in {wait}s...")
                        await asyncio.sleep(wait)

    except asyncio.CancelledError:
        print("[kafka] consumer shutting down...")
    finally:
        await consumer.stop()
        await dlq_producer.stop()
        print("[kafka] consumer stopped")