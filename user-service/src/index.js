require('dotenv').config();
const express     = require('express');
const swaggerUi  = require('swagger-ui-express');
const yaml        = require('js-yaml');
const fs          = require('fs');
const usersRouter = require('./routes/users');

// ── app must be created BEFORE anything calls app.use() ──
const app  = express();
const PORT = process.env.PORT || 3001;

app.use(express.json());

// Swagger UI — served at /docs
const path = require('path');
const spec = yaml.load(fs.readFileSync(path.join(__dirname, '..', 'openapi.yaml'), 'utf8'));
app.use('/docs', swaggerUi.serve, swaggerUi.setup(spec));

// Health check — Kubernetes liveness + readiness probe hits this
app.get('/health', (req, res) => {
  res.json({
    status:    'ok',
    service:   'user-service',
    uptime:    process.uptime(),
    timestamp: new Date().toISOString()
  });
});

// Routes
app.use('/users', usersRouter);

// 404 — must come after all routes
app.use((req, res) => {
  res.status(404).json({ error: 'Route not found' });
});

// Global error handler — must be last, always 4 arguments
app.use((err, req, res, next) => { // eslint-disable-line no-unused-vars
  console.error('[error]', err.message);
  res.status(500).json({ error: 'Internal server error' });
});

app.listen(PORT, () => {
  console.log('[user-service] listening on port', PORT);
  console.log('[user-service] docs at http://localhost:' + PORT + '/docs');
});

module.exports = app; // exported for supertest