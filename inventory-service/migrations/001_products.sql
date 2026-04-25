CREATE TABLE IF NOT EXISTS products (
    id                CHAR(36)       PRIMARY KEY DEFAULT (UUID()),
    sku               VARCHAR(100)   NOT NULL UNIQUE,
    name              VARCHAR(255)   NOT NULL,
    price             DECIMAL(10,2)  NOT NULL CHECK (price >= 0),
    stock_quantity    INT            NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    reserved_quantity INT            NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    updated_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_products_sku (sku)
);

-- Seed some products so we have data to work with immediately
INSERT INTO products (sku, name, price, stock_quantity) VALUES
    ('SKU-001', 'Laptop',     999.99, 50),
    ('SKU-002', 'Mouse',       29.99, 200),
    ('SKU-003', 'Keyboard',    79.99, 150),
    ('SKU-004', 'Monitor',    349.99, 30),
    ('SKU-005', 'Headphones',  99.99, 75);