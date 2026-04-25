package events

import (
	"context"
	"encoding/json"
	"log"
	"os"
	"time"

	"github.com/segmentio/kafka-go"
	"github.com/joho/godotenv"
)

// writer is the global Kafka producer — created once on startup
var writer *kafka.Writer

func InitProducer() {
	godotenv.Load()

	brokers := os.Getenv("KAFKA_BOOTSTRAP_SERVERS")
	if brokers == "" {
		brokers = "localhost:9092"
	}

	writer = &kafka.Writer{
		Addr:         kafka.TCP(brokers),
		Balancer:     &kafka.LeastBytes{}, // route to partition with least traffic
		RequiredAcks: kafka.RequireAll,    // wait for all replicas to acknowledge
		Async:        false,               // synchronous — wait for confirmation
		Logger:       log.New(os.Stdout, "[kafka-producer] ", 0),
	}

	log.Println("[kafka] producer initialised")
}

func Publish(topic string, key string, payload interface{}) error {
	if writer == nil {
		log.Println("[kafka] producer not initialised")
		return nil
	}

	// Serialize payload to JSON bytes
	value, err := json.Marshal(payload)
	if err != nil {
		return err
	}

	// Send message with timeout context
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	err = writer.WriteMessages(ctx,
		kafka.Message{
			Topic: topic,
			Key:   []byte(key),
			Value: value,
		},
	)
	if err != nil {
		log.Printf("[kafka] failed to publish to '%s': %v", topic, err)
		return err
	}

	log.Printf("[kafka] published to '%s' key='%s'", topic, key)
	return nil
}

func CloseProducer() {
	if writer != nil {
		writer.Close()
		log.Println("[kafka] producer closed")
	}
}