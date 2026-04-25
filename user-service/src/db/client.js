const { Pool } = require('pg');
require('dotenv').config();

// Pool manages multiple connections automatically.
// Max 10 simultaneous connections — tune per service load.
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 10,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 2000
});

// Log when a new connection is acquired (useful for debugging)
pool.on('connect', () => {
  console.log('[db] new connection acquired from pool');
});

pool.on('error', (err) => {
  console.error('[db] unexpected error on idle client', err);
  process.exit(-1);
});

// Test connection at startup so we fail fast if DB is unreachable
async function testConnection() {
  const client = await pool.connect();
  await client.query('SELECT 1');
  client.release();
  console.log('[db] connection OK');
}

testConnection().catch((err) => {
  console.error('[db] failed to connect:', err.message);
  process.exit(1);
});

module.exports = pool;