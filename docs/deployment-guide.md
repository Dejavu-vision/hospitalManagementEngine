# Deployment Guide

## Pipeline Flow
Every push to `main` triggers:
```
test → docker build & push (GHCR) → deploy to EC2
```

## GitHub Secrets Required
| Secret | Value |
|---|---|
| `EC2_SSH_PRIVATE_KEY` | Contents of your `.pem` file |
| `DB_PASSWORD` | `backenduser` |
| `JWT_SECRET` | JWT signing key (64+ chars) |

## EC2 One-Time Setup
```bash
ssh -i your-key.pem ec2-user@43.204.168.146
sudo yum install -y docker
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user
newgrp docker
```

## Health Check
```
http://43.204.168.146:8080/actuator/health
http://43.204.168.146:8080/actuator/info
```

## View Logs
```bash
docker logs hsm-app --tail 100 -f
# Persistent logs at:
tail -f /home/ec2-user/hsm-logs/hsm-$(date +%Y-%m-%d).log
```

## Rollback
```bash
docker stop hsm-app && docker rm hsm-app
docker run -d --name hsm-app --restart unless-stopped \
  -p 8080:8080 \
  -v /home/ec2-user/hsm-logs:/app/logs \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://43.204.168.146:3306/hospitalsystems" \
  -e SPRING_DATASOURCE_USERNAME="backenduser" \
  -e SPRING_DATASOURCE_PASSWORD="backenduser" \
  -e JWT_SECRET="curamatrix-hsm-secret-key-for-jwt-token-generation-minimum-512-bits" \
  -e SPRING_PROFILES_ACTIVE=prod \
  ghcr.io/dejavu-vision/curamatrix-hsm:<PREVIOUS_SHA>
```

## Local Development
```bash
docker-compose up -d
# App: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
```
