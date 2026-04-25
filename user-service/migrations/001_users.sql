CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- needed for gen_random_uuid()

CREATE TABLE IF NOT EXISTS users (
  id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  email      TEXT        NOT NULL UNIQUE,
  name       TEXT        NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Fast lookups by email (login, duplicate checks)
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);