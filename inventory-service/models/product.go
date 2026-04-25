package models

import "time"

// Product represents a product in the inventory
type Product struct {
    ID                string    `json:"id"                 db:"id"`
    SKU               string    `json:"sku"                db:"sku"`
    Name              string    `json:"name"               db:"name"`
    Price             float64   `json:"price"              db:"price"`
    StockQuantity     int       `json:"stock_quantity"     db:"stock_quantity"`
    ReservedQuantity  int       `json:"reserved_quantity"  db:"reserved_quantity"`
    AvailableQuantity int       `json:"available_quantity" db:"-"` // computed, not stored
    UpdatedAt         time.Time `json:"updated_at"         db:"updated_at"`
}

// Available returns how much stock can actually be sold
func (p *Product) Available() int {
    return p.StockQuantity - p.ReservedQuantity
}

// CreateProductRequest — body for POST /products
type CreateProductRequest struct {
    SKU           string  `json:"sku"            binding:"required"`
    Name          string  `json:"name"           binding:"required"`
    Price         float64 `json:"price"          binding:"required,gt=0"`
    StockQuantity int     `json:"stock_quantity"  binding:"required,gte=0"`
}

// UpdateStockRequest — body for PUT /products/:id/stock
type UpdateStockRequest struct {
    Quantity int    `json:"quantity" binding:"required"`
    Action   string `json:"action"   binding:"required,oneof=add subtract set"`
}

// OrderCreatedEvent — shape of the Kafka event from Order Service
type OrderCreatedEvent struct {
    OrderID   string      `json:"order_id"`
    UserID    string      `json:"user_id"`
    Total     float64     `json:"total"`
    Items     []OrderItem `json:"items"`
    CreatedAt string      `json:"created_at"`
}

// OrderItem — single item inside an order
type OrderItem struct {
    ProductID string  `json:"product_id"`
    Name      string  `json:"name"`
    Quantity  int     `json:"quantity"`
    Price     float64 `json:"price"`
}

// StockEvent — published to Kafka after processing an order
type StockEvent struct {
    OrderID string `json:"order_id"`
    UserID  string `json:"user_id"`
    Total   float64 `json:"total"`  
    Status  string `json:"status"` // "reserved" or "insufficient"
    Reason  string `json:"reason,omitempty"`
}