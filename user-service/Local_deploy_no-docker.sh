#!/bin/bash
# Run the user service locally without Docker
# Install dependencies
npm install

# Start a local postgres (if you have Docker)
docker run -d --name pg   -e POSTGRES_DB=userdb   -e POSTGRES_USER=user   -e POSTGRES_PASSWORD=password   -p 5432:5432 postgres:15-alpine

# verify that the database is running
docker ps | grep pg
# Run the migration
docker exec -i pg psql -U user -d userdb < migrations/001_users.sql

# Start the service
npm run dev

