# CuraMatrix HSM — Documentation

Hospital Management System — Multi-Tenant SaaS Platform

## Quick Links

| Document | Audience | Description |
|---|---|---|
| [Setup Guide](setup-guide.md) | Everyone | Local setup, DB, run the app |
| [API Flow Guide](api-flow.md) | Everyone | End-to-end API execution walkthrough |
| [Tenant Guide](tenant-guide.md) | Super Admin / DevOps | Hospital onboarding, subscriptions, isolation |
| [Backend Guide](backend-guide.md) | Backend Engineers | Architecture, entities, services, JWT |
| [Frontend Guide](frontend-guide.md) | Frontend Engineers | All endpoints, request/response, auth |
| [Product Guide](product-guide.md) | Product / QA | Roles, workflows, business rules |

## Live URLs

| Environment | URL |
|---|---|
| Production API | http://43.204.168.146:8080 |
| Swagger UI | http://43.204.168.146:8080/swagger-ui.html |
| Health Check | http://43.204.168.146:8080/actuator/health |
| Local | http://localhost:8080 |

## Tech Stack

- Java 17 + Spring Boot 3.x
- MySQL 8.0
- JWT Authentication
- Docker + GitHub Actions CI/CD
- AWS EC2 (Elastic IP: 43.204.168.146)
- GitHub Container Registry (GHCR)
