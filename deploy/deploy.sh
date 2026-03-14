#!/bin/bash
# ============================================================
# CuraMatrix HSM - EC2 Setup Script (Amazon Linux 2 / AL2023)
# Run this ONCE on your EC2 instance to install Docker
# ============================================================

set -e

echo "==> Installing Docker..."
sudo yum update -y
sudo yum install -y docker
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker $USER

echo ""
echo "==> Docker installed: $(sudo docker --version)"
echo ""
echo "==> IMPORTANT: Log out and log back in for group changes to take effect."
echo "    Then run: docker --version"
echo ""
echo "==> EC2 is ready. GitHub Actions will handle all future deploys automatically."
