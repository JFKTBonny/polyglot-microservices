#!/bin/bash
set -e

DOCKER_USER=${1:-santonix}
VERSION=${2:-v1.0.0}

GREEN='\033[0;32m'
NC='\033[0m'

SERVICES=(
  "user-service"
  "order-service"
  "inventory-service"
  "payment-service"
  "analytics-service"
  "auth-service"
  "notification-service"
  "api-gateway"
  "ui-service"
)

echo "[Info] Building all images..."
docker compose build

echo "[Info] Tagging and pushing to Docker Hub..."

for service in "${SERVICES[@]}"; do

  echo "[Info:  tagging and pushing] $service"

  docker tag polyglot-microservices-$service $DOCKER_USER/$service:$VERSION
  docker tag polyglot-microservices-$service $DOCKER_USER/$service:latest
  docker push $DOCKER_USER/$service:$VERSION
  docker push $DOCKER_USER/$service:latest

  echo -e "[Info: ]  ${GREEN}✓ $service pushed${NC}"
done

echo ""
echo -e "[Info..] ${GREEN}All images pushed to Docker Hub as $DOCKER_USER/*:$VERSION${NC}"

# To run : ./scripts/push-images.sh santonix v1.0.0
# if those arguments are not provided, the default ones will be used