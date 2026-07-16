# Slate API

Spring Boot REST API for SlateVN — workspaces, boards, tasks, RBAC, custom fields, JWT auth.

## Stack

- Spring Boot 3.4, Java 21
- JPA, Flyway, Spring Security + JWT
- PostgreSQL

## Quick start (Docker)

```bash
docker compose up --build
```

- API: http://localhost:8080
- Default admin: `admin@slatevn.local` / `Admin123!`

## Local development

### 1. PostgreSQL

```bash
docker compose up -d postgres
```

### 2. Run API

```bash
mvn spring-boot:run
```

Copy `.env.example` → `.env` and adjust values as needed.

## Environment variables

| Variable | Default |
|----------|---------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/slatevn` |
| `DATABASE_USERNAME` | `slatevn` |
| `DATABASE_PASSWORD` | `slatevn` |
| `JWT_SECRET` | (change in production) |
| `ADMIN_EMAIL` | `admin@slatevn.local` |
| `ADMIN_PASSWORD` | `Admin123!` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` |

When the frontend is hosted separately, set `CORS_ALLOWED_ORIGINS` to the frontend URL(s).

## Main API routes

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/login` | Login |
| POST | `/api/auth/refresh` | Refresh token |
| GET | `/api/auth/me` | Current user |
| GET/POST | `/api/users` | User management |
| GET/POST | `/api/workspaces` | Workspaces |
| GET/POST | `/api/workspaces/{id}/boards` | Boards |
| GET | `/api/boards/{id}` | Board view |
| POST | `/api/boards/{id}/tasks` | Create task |
| PUT | `/api/tasks/{id}` | Update task |
| POST | `/api/tasks/{id}/move` | Move task column |

Frontend: [slate-web](https://github.com/letuankiet146/slate-web)
