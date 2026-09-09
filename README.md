# Homeopathy Clinic Starter

Cloud-first starter for:
- React + TypeScript UI
- Java 21 + Spring Boot API
- PostgreSQL cloud database
- Docker images for frontend and backend
- GitHub Codespaces development

## 1. Create a cloud PostgreSQL database
Create a development PostgreSQL database and copy:
- JDBC URL
- username
- password

Use test/fake patient data during development.

## 2. Add GitHub Codespaces secrets
Create these Codespaces secrets:
- DATABASE_URL
- DATABASE_USERNAME
- DATABASE_PASSWORD

DATABASE_URL example:
`jdbc:postgresql://host/database?sslmode=require`

## 3. Run backend
```bash
cd backend
mvn spring-boot:run
```

Health check:
`GET /api/health`

## 4. Run frontend
Open a second terminal:
```bash
cd frontend
npm run dev
```

## 5. Docker build
From project root:
```bash
docker compose build
```

## Security note
Do not commit passwords, connection strings, JWT secrets, or real patient data.
The initial backend uses HTTP Basic only as a placeholder. Replace it with production authentication before using real data.
