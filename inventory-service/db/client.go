package db

import (
    "database/sql"
    "fmt"
    "log"
    "os"
    "time"

    _ "github.com/go-sql-driver/mysql"
    "github.com/joho/godotenv"
)

// DB is the global connection pool — used across all route handlers
var DB *sql.DB

func Init() {
    // Load .env file — ignored if running in Docker (env vars already set)
    godotenv.Load()

    host     := os.Getenv("DB_HOST")
    port     := os.Getenv("DB_PORT")
    user     := os.Getenv("DB_USER")
    password := os.Getenv("DB_PASSWORD")
    dbName   := os.Getenv("DB_NAME")

    // DSN — Data Source Name — MariaDB connection string format
    dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?parseTime=true",
        user, password, host, port, dbName,
    )

    var err error
    DB, err = sql.Open("mysql", dsn)
    if err != nil {
        log.Fatalf("[db] failed to open connection: %v", err)
    }

    // Connection pool settings
    DB.SetMaxOpenConns(10)
    DB.SetMaxIdleConns(5)
    DB.SetConnMaxLifetime(time.Minute * 3)

    // Verify connection is actually working — fail fast
    if err := DB.Ping(); err != nil {
        log.Fatalf("[db] failed to ping database: %v", err)
    }

    log.Println("[db] connection pool ready")
}

func Close() {
    if DB != nil {
        DB.Close()
        log.Println("[db] connection pool closed")
    }
}