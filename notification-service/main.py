import asyncio
import os
from dotenv import load_dotenv
from consumer import start_consumer

load_dotenv()

if __name__ == "__main__":
    print("[notification-service] starting up...")
    print(f"[notification-service] connecting to Kafka at {os.getenv('KAFKA_BOOTSTRAP_SERVERS')}")
    asyncio.run(start_consumer())