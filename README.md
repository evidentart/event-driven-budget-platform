# Event-Driven-Budget-Platform

Distributed event-driven personal finance platform built with Spring Boot microservices. Uses Kafka for domain events, RabbitMQ for async notifications, Keycloak for JWT-based authentication, and integrates Google Gemini API to generate AI-powered financial insights and budget recommendations.

## Why This Project Matters

This project demonstrates:

- Microservices architecture with clear service boundaries
- Secure authentication and authorization with Keycloak + JWT
- Event-driven design using Kafka and RabbitMQ
- Mixed communication patterns (REST + gRPC + async messaging)
- Polyglot persistence (PostgreSQL + MongoDB)
- Resilience patterns and graceful fallbacks
- Dockerized microservices and infrastructure components for consistent local/dev environments

## System Flow

1. User logs in from the frontend through Keycloak.
2. API Gateway validates JWT and routes requests to backend services.
3. On first profile request, `user-service` auto-creates a user record if missing.
4. User creates an expense in `expense-service`.
5. `expense-service` calls `budget-service` via gRPC to get budget status/warning before saving.
6. Expense is saved to PostgreSQL and published to Kafka.
7. `budget-service` consumes the Kafka event, updates budget data in PostgreSQL, and publishes a budget event to RabbitMQ.
8. `ai-service` consumes the RabbitMQ event, generates insights, and stores them in MongoDB.
9. Frontend fetches budget and provides AI insight APIs for user-facing warnings and recommendations.

## Architecture
![Architecture Diagram](architecture_diagram.png)

```text
Frontend (React + Vite)
  -> Keycloak Login
  -> API Gateway (Spring Cloud Gateway, JWT)

Gateway routes:
  /api/users/**    -> user-service
  /api/expenses/** -> expense-service
  /api/budgets/**  -> budget-service
  /api/insights/** -> ai-service

Event pipeline:
expense-service -> Kafka -> budget-service -> RabbitMQ -> ai-service
```

## Tech Stack

- Frontend: React, Vite, MUI, React Query, Keycloak JS
- Backend: Java 21, Spring Boot 4, Spring Security, Spring Data JPA
- API Gateway: Spring Cloud Gateway (WebFlux)
- Sync communication: REST, gRPC
- Async communication: Kafka, RabbitMQ
- Databases: PostgreSQL (user/expense/budget), MongoDB (AI insights)
- AI integration: Gemini API (with fallback behavior)
- Containerization: Docker (service-level containerization)

## Services

| Service | Port | Responsibility | Database |
|---|---:|---|---|
| api-gateway | 8080 | Auth + request routing | - |
| user-service | 4000 | User profile, get-or-create on first login | PostgreSQL |
| expense-service | 4001 | Expense CRUD, gRPC budget pre-check, Kafka producer | PostgreSQL |
| budget-service | 4002 (HTTP), 9001 (gRPC) | Budget logic, Kafka consumer, RabbitMQ producer | PostgreSQL |
| ai-service | 4003 | RabbitMQ consumer, AI insights API | MongoDB |
| smart-expense-frontend | 5173 (dev) | UI/dashboard | - |

## Example API

### Create Expense

`POST /api/expenses`

```json
{
  "userId": "11111111-1111-1111-1111-111111111111",
  "title": "Grocery Run",
  "description": "Weekly groceries",
  "amount": 86.45,
  "category": "FOOD",
  "expenseDate": "2026-02-18T18:30:00"
}
```

## Run with Docker 

### Prerequisites

- Docker

> This repo uses **service-level Dockerization** (no Docker Compose/Kubernetes included). Start each container individually and ensure networking/environment variables are configured so services can reach their dependencies.

### Typical Workflow (per service)

From each service directory:

```bash
# Build image
docker build -t <service-name>:latest .

# Run container (example: user-service)
docker run --name user-service -p 4000:4000 <service-name>:latest
```

### Containerized Components

- api-gateway
- user-service
- expense-service
- budget-service
- ai-service
- Keycloak
- Kafka
- RabbitMQ
- PostgreSQL
- MongoDB

## Environment Variables (Typical)

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`
- `SPRING_DATA_MONGODB_URI`
- `RABBITMQ_QUEUE_NAME`
- `RABBITMQ_EXCHANGE_NAME`
- `RABBITMQ_ROUTING_KEY`
- `GEMINI_API_KEY` 

## License

Copyright © 2026 Ali Akcin.

This project is published for portfolio purposes only.  
Unauthorized copying, redistribution, or commercial use of this codebase is prohibited without prior written consent.
