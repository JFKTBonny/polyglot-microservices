const router = require('express').Router();
const db     = require('../db/client');
const { validate } = require('../middleware/validate');

// GET /users — list all (paginated)
router.get('/', async (req, res, next) => {
  try {
    const limit  = Math.min(parseInt(req.query.limit)  || 20, 100);
    const offset = parseInt(req.query.offset) || 0;
    const { rows } = await db.query(
      'SELECT id, email, name, created_at FROM users ORDER BY created_at DESC LIMIT $1 OFFSET $2',
      [limit, offset]
    );
    res.json({ data: rows, limit, offset });
  } catch (err) { next(err); }
});

// GET /users/:id
router.get('/:id', async (req, res, next) => {
  try {
    const { rows } = await db.query(
      'SELECT id, email, name, created_at FROM users WHERE id = $1',
      [req.params.id]
    );
    if (!rows.length) return res.status(404).json({ error: 'User not found' });
    res.json(rows[0]);
  } catch (err) { next(err); }
});

// POST /users — create
router.post('/', validate(['email', 'name']), async (req, res, next) => {
  const { email, name } = req.body;
  try {
    const { rows } = await db.query(
      'INSERT INTO users(email, name) VALUES($1, $2) RETURNING *',
      [email.toLowerCase().trim(), name.trim()]
    );
    res.status(201).json(rows[0]);
  } catch (err) {
    if (err.code === '23505') // unique_violation
      return res.status(409).json({ error: 'Email already registered' });
    next(err);
  }
});

// PUT /users/:id — update name
router.put('/:id', async (req, res, next) => {
  const { name } = req.body;
  if (!name) return res.status(400).json({ error: 'name is required' });
  try {
    const { rows } = await db.query(
      'UPDATE users SET name=$1 WHERE id=$2 RETURNING *',
      [name.trim(), req.params.id]
    );
    if (!rows.length) return res.status(404).json({ error: 'User not found' });
    res.json(rows[0]);
  } catch (err) { next(err); }
});

// DELETE /users/:id
router.delete('/:id', async (req, res, next) => {
  try {
    const { rowCount } = await db.query(
      'DELETE FROM users WHERE id = $1',
      [req.params.id]
    );
    if (!rowCount) return res.status(404).json({ error: 'User not found' });
    res.status(204).send();
  } catch (err) { next(err); }
});

module.exports = router;