# Deployment Guide — CuraMatrix HSM

## How it works

Every push to `main` triggers the GitHub Actions pipeline:

```
test → docker (build & push to GHCR) → deploy (SSH to EC2)
```

Pushes to `develop` run test + docker but skip deploy.
Pull requests to `main` only run tests.

---

## One-time EC2 Setup

1. Launch an EC2 instance (Ubuntu 22.04, t3.small minimum)
2. Open inbound ports: 22 (SSH), 80 (HTTP), 8080 (API)
3. SSH in and run the setup script:

```bash
bash deploy/deploy.sh
```

---

## GitHub Secrets

Go to **Settings → Secrets and variables → Actions** and add:

| Secret | Description | Example |
|---|---|---|
| `EC2_HOST` | EC2 public IP or domain | `54.123.45.67` |
| `EC2_USER` | SSH user | `ubuntu` |
| `EC2_SSH_PRIVATE_KEY` | Contents of your `.pem` file | `-----BEGIN RSA...` |
| `DB_URL` | JDBC connection string | `jdbc:mysql://rds-host:3306/hospitalsystems` |
| `DB_USERNAME` | DB user | `admin` |
| `DB_PASSWORD` | DB password | `yourpassword` |
| `JWT_SECRET` | JWT signing key (64+ chars) | `curamatrix-...` |

> `GITHUB_TOKEN` is automatically provided by GitHub Actions — no setup needed.

---

## Docker Image Registry

Images are pushed to **GitHub Container Registry (GHCR)**:
```
ghcr.io/<your-github-username>/curamatrix-hsm:latest
ghcr.io/<your-github-username>/curamatrix-hsm:<commit-sha>
```

---

## Local Development

```bash
# Start app + MySQL together
docker-compose up -d

# App: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

---

## Database

For production, use **Amazon RDS (MySQL 8.0)**. Run the schema once:

```bash
mysql -h <RDS_HOST> -u admin -p hospitalsystems < docs/COMPLETE_SAAS_SCHEMA.sql
```

---

## Rollback

SSH into EC2 and run a previous image by commit SHA:

```bash
docker stop hsm-app && docker rm hsm-app
docker run -d --name hsm-app --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL="..." \
  -e SPRING_DATASOURCE_USERNAME="..." \
  -e SPRING_DATASOURCE_PASSWORD="..." \
  -e JWT_SECRET="..." \
  -e SPRING_PROFILES_ACTIVE=prod \
  ghcr.io/<your-github-username>/curamatrix-hsm:<PREVIOUS_SHA>
```
