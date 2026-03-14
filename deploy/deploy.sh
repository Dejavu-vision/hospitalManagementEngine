#!/bin/bash
# ============================================================
# CuraMatrix HSM - EC2 Deployment Script
# Run this once on your EC2 instance to set up Docker
# ============================================================

set -e

echo "==> Installing Docker..."
sudo apt-get update -y
sudo apt-get install -y docker.io docker-compose-plugin
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker $USER

echo "==> Docker installed: $(docker --version)"
echo ""
echo "==> EC2 is ready for deployments."
echo "    GitLab CI/CD will handle all future deploys automatically."
echo ""
echo "==> Required GitLab CI/CD Variables (Settings > CI/CD > Variables):"
echo "    EC2_HOST           - Your EC2 public IP or domain"
echo "    EC2_USER           - EC2 SSH user (e.g. ubuntu)"
echo "    EC2_SSH_PRIVATE_KEY - Your EC2 private key (.pem contents)"
echo "    DB_URL             - jdbc:mysql://<RDS_HOST>:3306/hospitalsystems"
echo "    DB_USERNAME        - Database username"
echo "    DB_PASSWORD        - Database password"
echo "    JWT_SECRET         - JWT signing secret (min 64 chars)"
echo "    CI_REGISTRY_USER   - GitLab registry user (auto-provided)"
echo "    CI_REGISTRY_PASSWORD - GitLab registry password (auto-provided)"
