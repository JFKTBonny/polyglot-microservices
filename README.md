# Microservices Platform

A production-grade microservices system built with 6 different languages and frameworks.

## Services

| Service | Language | Framework | Database | Port |
|---|---|---|---|---|
| User Service | Node.js | Express | PostgreSQL | 3001 |
| Order Service | Python | FastAPI | MySQL | 3002 |
| Inventory Service | Go | Gin | MariaDB | 3003 |
| Payment Service | Java | Spring Boot | PostgreSQL | 3004 |
| Analytics Service | PHP | Laravel | PostgreSQL | 3005 |
| Notification Service | Python | aiokafka | — | — |
| API Gateway | Nginx | — | — | 80 |
| UI Service | HTML5/CSS/JS | Nginx | — | 3000 |

## Architecture
Browser
└── Nginx API Gateway :80
├── /api/users      → User Service
├── /api/orders     → Order Service
├── /api/products   → Inventory Service
├── /api/payments   → Payment Service
├── /api/analytics  → Analytics Service
└── /               → UI Service
## Event Flow
order.created → Inventory Service → stock.reserved → Payment Service
→ payment.processed
→ Notification Service (email)
→ Analytics Service (metrics)

## Quick Start

```bash
# Clone the repository
git clone git@github.com:<username>/microservices.git
cd microservices

# Copy environment files
cp user-service/.env.example user-service/.env
cp order-service/.env.example order-service/.env
# ... repeat for each service

# Start everything
docker compose up -d

# Verify all services are healthy
./scripts/test-all.sh
```

## Planned Features

- [ ] JWT Authentication (feature/security-jwt)
- [ ] Circuit Breaker (feature/resilience-circuit-breaker)
- [ ] Kubernetes deployment (feature/kubernetes)
- [ ] CI/CD pipeline (feature/ci-cd)
- [ ] Observability stack (feature/observability)

## Branch Strategy

- `main` — production ready, protected
- `develop` — integration branch
- `feature/*` — one feature per branch, merged to develop via PR
- `hotfix/*` — emergency fixes from main
