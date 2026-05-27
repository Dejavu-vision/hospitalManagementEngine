# DeployProd.ps1
# -------------------------------------------------
# This script automates the production deployment steps for the Hospital Management System.
# It assumes you are running on a Windows host with Docker Desktop installed.
# -------------------------------------------------
# 1️⃣ Ensure you are in the project root (where docker-compose.yml lives)
Set-Location "C:\Users\Acer\Hospital\hospitalManagementEngine"

# 2️⃣ If a .env file does NOT exist, create one from the example and prompt you to edit it.
if (-Not (Test-Path ".env")) {
    Write-Host "Creating .env from .env.example..."
    Copy-Item -Path ".env.example" -Destination ".env" -Force
    Write-Host "Please open .env and replace the placeholder values (especially DEEPGRAM_API_KEY)."
    # Open Notepad for the user to edit
    notepad ".env"
}

# 3️⃣ Stop any existing stack (preserves volumes)
Write-Host "Stopping existing Docker Compose stack (if any)..."
docker compose down

# 4️⃣ Pull the latest image (optional – uncomment if you push to a remote registry)
# Write-Host "Pulling latest image from registry..."
# docker compose pull

# 5️⃣ Bring the stack up in detached mode
Write-Host "Starting Docker Compose stack..."
docker compose up -d

# 6️⃣ Tail the logs and verify the Deepgram key was loaded
Write-Host "Waiting for container to start..."
Start-Sleep -Seconds 5
Write-Host "Fetching logs..."
if (docker compose ps -q hsm-app) {
    docker compose stop hsm-app | Out-Null
    docker compose rm -f hsm-app | Out-Null
    Write-Host "Old hsm-app service stopped and removed."
} else {
    Write-Host "No existing hsm-app service found."
}
$log = docker compose logs hsm-app 2>&1
Write-Host $log
if ($log -match "Deepgram API key loaded") {
    Write-Host "✅ Deepgram API key successfully loaded."
} else {
    Write-Host "⚠️ Deepgram API key NOT loaded. Check .env and ensure DEEPGRAM_API_KEY is set."
}

# 7️⃣ Open the application in the default browser (adjust URL if needed)
$appUrl = "http://localhost:8080"
Write-Host "Opening application at $appUrl"
Start-Process $appUrl

# -------------------------------------------------
# End of DeployProd.ps1
# -------------------------------------------------
