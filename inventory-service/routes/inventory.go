package routes

import (
	"database/sql"
	"log"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"inventory-service/db"
	"inventory-service/models"
)

func RegisterRoutes(r *gin.Engine) {
	r.GET("/products",           listProducts)
	r.GET("/products/:id",       getProduct)
	r.POST("/products",          createProduct)
	r.PUT("/products/:id/stock", updateStock)
}

// ── GET /products ──────────────────────────────────────────────────────────
func listProducts(c *gin.Context) {
	rows, err := db.DB.Query(
		`SELECT id, sku, name, price,
			stock_quantity, reserved_quantity, updated_at
		 FROM products ORDER BY name ASC`,
	)
	if err != nil {
		log.Printf("[error] listProducts: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch products"})
		return
	}
	defer rows.Close()

	products := []models.Product{}
	for rows.Next() {
		var p models.Product
		err := rows.Scan(
			&p.ID, &p.SKU, &p.Name, &p.Price,
			&p.StockQuantity, &p.ReservedQuantity, &p.UpdatedAt,
		)
		if err != nil {
			log.Printf("[error] scan: %v", err)
			continue
		}
		p.AvailableQuantity = p.Available()
		products = append(products, p)
	}

	c.JSON(http.StatusOK, gin.H{
		"data":  products,
		"count": len(products),
	})
}

// ── GET /products/:id ──────────────────────────────────────────────────────
func getProduct(c *gin.Context) {
	id := c.Param("id")

	var p models.Product
	err := db.DB.QueryRow(
		`SELECT id, sku, name, price,
			stock_quantity, reserved_quantity, updated_at
		 FROM products WHERE id = ?`,
		id,
	).Scan(
		&p.ID, &p.SKU, &p.Name, &p.Price,
		&p.StockQuantity, &p.ReservedQuantity, &p.UpdatedAt,
	)

	if err == sql.ErrNoRows {
		c.JSON(http.StatusNotFound, gin.H{"error": "product not found"})
		return
	}
	if err != nil {
		log.Printf("[error] getProduct: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch product"})
		return
	}

	p.AvailableQuantity = p.Available()
	c.JSON(http.StatusOK, p)
}

// ── POST /products ─────────────────────────────────────────────────────────
func createProduct(c *gin.Context) {
	var body models.CreateProductRequest

	if err := c.ShouldBindJSON(&body); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	_, err := db.DB.Exec(
		`INSERT INTO products (sku, name, price, stock_quantity)
		 VALUES (?, ?, ?, ?)`,
		body.SKU, body.Name, body.Price, body.StockQuantity,
	)
	if err != nil {
		if strings.Contains(err.Error(), "Duplicate entry") {
			c.JSON(http.StatusConflict, gin.H{"error": "SKU already exists"})
			return
		}
		log.Printf("[error] createProduct: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create product"})
		return
	}

	var p models.Product
	err = db.DB.QueryRow(
		`SELECT id, sku, name, price,
			stock_quantity, reserved_quantity, updated_at
		 FROM products WHERE sku = ?`,
		body.SKU,
	).Scan(
		&p.ID, &p.SKU, &p.Name, &p.Price,
		&p.StockQuantity, &p.ReservedQuantity, &p.UpdatedAt,
	)
	if err != nil {
		log.Printf("[error] fetch after insert: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "product created but fetch failed"})
		return
	}

	p.AvailableQuantity = p.Available()
	c.JSON(http.StatusCreated, p)
}

// ── PUT /products/:id/stock ────────────────────────────────────────────────
func updateStock(c *gin.Context) {
	id := c.Param("id")

	var body models.UpdateStockRequest
	if err := c.ShouldBindJSON(&body); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	var query string
	switch body.Action {
	case "add":
		query = "UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ?"
	case "subtract":
		query = "UPDATE products SET stock_quantity = stock_quantity - ? WHERE id = ?"
	case "set":
		query = "UPDATE products SET stock_quantity = ? WHERE id = ?"
	default:
		c.JSON(http.StatusBadRequest, gin.H{"error": "action must be add, subtract or set"})
		return
	}

	result, err := db.DB.Exec(query, body.Quantity, id)
	if err != nil {
		log.Printf("[error] updateStock: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update stock"})
		return
	}

	rowsAffected, _ := result.RowsAffected()
	if rowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "product not found"})
		return
	}

	var p models.Product
	err = db.DB.QueryRow(
		`SELECT id, sku, name, price,
			stock_quantity, reserved_quantity, updated_at
		 FROM products WHERE id = ?`,
		id,
	).Scan(
		&p.ID, &p.SKU, &p.Name, &p.Price,
		&p.StockQuantity, &p.ReservedQuantity, &p.UpdatedAt,
	)
	if err != nil {
		log.Printf("[error] fetch after update: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "stock updated but fetch failed"})
		return
	}

	p.AvailableQuantity = p.Available()
	c.JSON(http.StatusOK, p)
}