CREATE TABLE IF NOT EXISTS orders (
  id         CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
  user_id    CHAR(36)     NOT NULL,
  status     VARCHAR(50)  NOT NULL DEFAULT 'pending',
  total      DECIMAL(10,2) NOT NULL CHECK (total >= 0),
  items      JSON         NOT NULL,
  created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

  INDEX idx_orders_user_id (user_id),
  INDEX idx_orders_status  (status)
);