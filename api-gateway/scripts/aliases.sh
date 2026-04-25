# ── Gateway shortcuts ─────────────────────────────
alias ms-gateway="curl -s http://localhost/health | jq"

# Test all routes through gateway
alias ms-gateway-test='
  echo "── Gateway health ──"
  curl -s http://localhost/health | jq .status

  echo "── Users via gateway ──"
  curl -s http://localhost/api/users | jq .

  echo "── Products via gateway ──"
  curl -s http://localhost/api/products | jq .count

  echo "── Analytics via gateway ──"
  curl -s http://localhost/api/analytics/summary | jq .
'