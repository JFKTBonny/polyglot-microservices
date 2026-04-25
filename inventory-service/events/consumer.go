package events

import (
	"context"
	"encoding/json"
	"log"
	"os"
	"time"

	"github.com/segmentio/kafka-go"
	"github.com/joho/godotenv"
	"inventory-service/db"
	"inventory-service/models"
)

func StartConsumer() {
	godotenv.Load()

	brokers := os.Getenv("KAFKA_BOOTSTRAP_SERVERS")
	if brokers == "" {
		brokers = "localhost:9092"
	}

	// Wait for Kafka to fully initialise
	log.Println("[kafka] waiting 15s for Kafka to fully initialise...")
	time.Sleep(15 * time.Second)

	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:        []string{brokers},
		Topic:          "order.created",
		GroupID:        "inventory-service",
		MinBytes:       1,
		MaxBytes:       10e6,   // 10MB max message size
		CommitInterval: 0,      // manual commit only
		StartOffset:    kafka.FirstOffset,
		Logger:         log.New(os.Stdout, "[kafka-consumer] ", 0),
	})

	defer reader.Close()

	log.Println("[kafka] consumer started — listening on 'order.created'")
	log.Println("[kafka] group_id: inventory-service")

	for {
		// FetchMessage does NOT commit the offset automatically
		msg, err := reader.FetchMessage(context.Background())
		if err != nil {
			log.Printf("[kafka] error fetching message: %v", err)
			time.Sleep(2 * time.Second)
			continue
		}

		log.Printf("[kafka] received message partition=%d offset=%d",
			msg.Partition, msg.Offset)

		// Deserialize the event
		var event models.OrderCreatedEvent
		if err := json.Unmarshal(msg.Value, &event); err != nil {
			log.Printf("[kafka] failed to parse message: %v", err)
			// Commit anyway — malformed messages go to DLQ not retry loop
			commitMessage(reader, msg)
			publishToDLQ(msg, err)
			continue
		}

		// Process with retry
		if err := processOrderWithRetry(event, 3); err != nil {
			log.Printf("[kafka] all retries exhausted for order %s", event.OrderID)
			publishToDLQ(msg, err)
		}

		// Commit offset — only after processing is complete
		commitMessage(reader, msg)
	}
}

func processOrderWithRetry(event models.OrderCreatedEvent, maxRetries int) error {
	var err error
	for attempt := 1; attempt <= maxRetries; attempt++ {
		err = reserveStock(event)
		if err == nil {
			return nil
		}
		log.Printf("[kafka] attempt %d/%d failed: %v", attempt, maxRetries, err)
		time.Sleep(time.Duration(attempt*attempt) * time.Second) // exponential backoff
	}
	return err
}

func reserveStock(event models.OrderCreatedEvent) error {
	// Process each item in the order
	for _, item := range event.Items {
		// Check available stock for this product SKU
		var available int
		err := db.DB.QueryRow(
			`SELECT (stock_quantity - reserved_quantity) as available
			 FROM products WHERE sku = ?`,
			item.ProductID,
		).Scan(&available)

		if err != nil {
			log.Printf("[kafka] product SKU '%s' not found", item.ProductID)
			// Publish insufficient event — product doesn't exist
			return publishStockEvent(event, "insufficient",
				"product not found: "+item.ProductID)
		}

		if available < item.Quantity {
			log.Printf("[kafka] insufficient stock for SKU '%s': need %d have %d",
				item.ProductID, item.Quantity, available)
			return publishStockEvent(event, "insufficient",
				"insufficient stock for: "+item.ProductID)
		}
	}

	// All items have sufficient stock — reserve them all
	for _, item := range event.Items {
		_, err := db.DB.Exec(
			`UPDATE products
			 SET reserved_quantity = reserved_quantity + ?
			 WHERE sku = ?`,
			item.Quantity, item.ProductID,
		)
		if err != nil {
			return err
		}
		log.Printf("[kafka] reserved %d units of SKU '%s'",
			item.Quantity, item.ProductID)
	}

	// Publish success event
	return publishStockEvent(event, "reserved", "")
}

func publishStockEvent(event models.OrderCreatedEvent, status string, reason string) error {
	topic := "stock.reserved"
	if status == "insufficient" {
		topic = "stock.insufficient"
	}

	payload := models.StockEvent{
		OrderID: event.OrderID,
		UserID:  event.UserID,
		Total:   event.Total,
		Status:  status,
		Reason:  reason,
	}

	return Publish(topic, event.OrderID, payload)
}

func commitMessage(reader *kafka.Reader, msg kafka.Message) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := reader.CommitMessages(ctx, msg); err != nil {
		log.Printf("[kafka] failed to commit offset: %v", err)
	} else {
		log.Printf("[kafka] offset committed partition=%d offset=%d",
			msg.Partition, msg.Offset)
	}
}

func publishToDLQ(msg kafka.Message, err error) {
	type DLQMessage struct {
		OriginalTopic string `json:"original_topic"`
		OriginalValue string `json:"original_value"`
		Error         string `json:"error"`
	}

	payload := DLQMessage{
		OriginalTopic: "order.created",
		OriginalValue: string(msg.Value),
		Error:         err.Error(),
	}

	if pubErr := Publish("order.created.dlq", string(msg.Key), payload); pubErr != nil {
		log.Printf("[kafka] failed to publish to DLQ: %v", pubErr)
	} else {
		log.Println("[kafka] message sent to DLQ")
	}
}