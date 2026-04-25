package main

import (
	"log"
	"net/http"
	"os"

	"github.com/gin-gonic/gin"
	"github.com/joho/godotenv"
	"inventory-service/db"
	"inventory-service/events"
	"inventory-service/routes"
)

func main() {
	// Load .env — ignored silently in Docker
	godotenv.Load()

	port := os.Getenv("PORT")
	if port == "" {
		port = "3003"
	}

	// ── Initialise database pool ───────────────────────────────────────────
	db.Init()
	defer db.Close()

	// ── Initialise Kafka producer ──────────────────────────────────────────
	events.InitProducer()
	defer events.CloseProducer()

	// ── Start Kafka consumer in background goroutine ───────────────────────
	// This is the key difference from Python — no asyncio needed.
	// Go spawns a lightweight goroutine that runs concurrently
	// with the HTTP server on the same process.
	go events.StartConsumer()

	// ── Set up Gin router ──────────────────────────────────────────────────
	// Use ReleaseMode in production to disable debug logs
	if os.Getenv("GIN_MODE") == "release" {
		gin.SetMode(gin.ReleaseMode)
	}

	r := gin.Default()

	// ── Health check ───────────────────────────────────────────────────────
	r.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"status":  "ok",
			"service": "inventory-service",
		})
	})

	// ── Register all routes ────────────────────────────────────────────────
	routes.RegisterRoutes(r)

	// ── Start HTTP server ──────────────────────────────────────────────────
	log.Printf("[inventory-service] listening on port %s", port)
	log.Printf("[inventory-service] docs at http://localhost:%s/products", port)

	if err := r.Run(":" + port); err != nil {
		log.Fatalf("[inventory-service] failed to start: %v", err)
	}
}