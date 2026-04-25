// Generic validation middleware factory.
// Usage: validate(['email','name']) as route middleware.
function validate(requiredFields) {
  return (req, res, next) => {
    const missing = requiredFields.filter(f => !req.body[f]);
    if (missing.length) {
      return res.status(400).json({
        error: 'Missing required fields',
        fields: missing
      });
    }
    // Basic email format check
    if (req.body.email && !req.body.email.includes('@')) {
      return res.status(400).json({ error: 'Invalid email format' });
    }
    next();
  };
}

module.exports = { validate };