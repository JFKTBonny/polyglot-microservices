#!/bin/bash
# Test all endpoints of the user service
# Make sure the service is running before executing this script
# Health check
curl http://localhost:3001/health

# Create a user
curl -s -X POST http://localhost:3001/users   -H "Content-Type: application/json"   -d '{"email":"alice@example.com","name":"Alice"}' | jq

# List users
curl -s http://localhost:3001/users | jq

# Get by ID (paste the id from the create response)
curl -s http://localhost:3001/users/<id> | jq

# Update name
curl -s -X PUT http://localhost:3001/users/<id>   -H "Content-Type: application/json"   -d '{"name":"Alice Smith"}' | jq

# Delete
curl -s -X DELETE http://localhost:3001/users/<id> -w "%{http_code}"

# Try duplicate email — expect 409
curl -s -X POST http://localhost:3001/users   -H "Content-Type: application/json"   -d '{"email":"alice@example.com","name":"Alice Again"}' | jq