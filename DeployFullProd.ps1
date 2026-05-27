<# DeployFullProd.ps1
   Full production‑style deployment that:
   • Guarantees the MySQL init script exists (or disables it)
   • Waits for the DB to become healthy
   • Starts the Spring back‑end with the Deepgram key
   • Starts the React front‑end (npm run dev) in ../HospitalManagment/hospital-management
   • Performs verification steps and prints clear results
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# -------------------------------------------------
# 0️⃣ Paths
$Root          = 'C:\Users\Acer\Hospital\hospitalManagementEngine'   # back‑end repo
$FrontEndRoot  = 'C:\Users\Acer\Hospital\HospitalManagment\hospital-management' # React repo
$EnvFile       = Join-Path $Root '.env'
$ComposeFile   = Join-Path $Root 'docker-compose.yml'
$LogFile       = Join-Path $Root 'deploy_full_prod.log'

# -------------------------------------------------
# 1️⃣ Ensure .env has a real Deepgram key
Write-Host "`n=== Ensuring .env contains DEEPGRAM_API_KEY ===`n"
if (-not (Test-Path $EnvFile)) {
    $secure = Read-Host -Prompt 'Enter your REAL Deepgram API key (input hidden)' -AsSecureString
$newKey = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
    $envContent = @"
DEEPGRAM_API_KEY=$newKey
DB_URL=jdbc:mysql://db:3306/hospitalsystems?useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=root
JWT_SECRET=curamatrix-hsm-secret-key-for-jwt-token-generation-minimum-512-bits
"@
    Set-Content -Path $EnvFile -Value $envContent -Encoding UTF8
    Write-Host ".env created with Deepgram key."
} else {
    $content = Get-Content $EnvFile -Raw
    if ($content -notmatch '^DEEPGRAM_API_KEY=') {
        $secure = Read-Host -Prompt 'Enter your REAL Deepgram API key (input hidden)' -AsSecureString
        $newKey = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
        $content += "`r`nDEEPGRAM_API_KEY=$newKey"
        Set-Content -Path $EnvFile -Value $content -Encoding UTF8
        Write-Host ".env updated with Deepgram key."
    } else {
        Write-Host ".env already contains DEEPGRAM_API_KEY."
    }
}

# -------------------------------------------------
# 2️⃣ Make sure the optional MySQL init script exists.
#    If it does not, we simply comment‑out the volume line so MySQL can start.
$initSql = Join-Path $Root 'docs\COMPLETE_SAAS_SCHEMA.sql'
if (-Not (Test-Path $initSql)) {
    Write-Host "MySQL init script not found – disabling that mount in docker-compose.yml"
    (Get-Content $ComposeFile) | ForEach-Object { if ($_ -match 'COMPLETE_SAAS_SCHEMA.sql') { "#$_   # disabled - file not present" } else { $_ } } | Set-Content $ComposeFile -Encoding UTF8
}

# -------------------------------------------------
# 3️⃣ Stop any old compose stack completely
Write-Host "`n=== Stopping any existing compose stack ===`n"
docker compose -f $ComposeFile down --remove-orphans | Out-Null

# -------------------------------------------------
# 4️⃣ Build the Spring image (fresh)
Write-Host "`n=== Building Spring image (hsm-app:latest) ===`n"
docker build -t hsm-app:latest $Root | Tee-Object -FilePath $LogFile

# -------------------------------------------------
# 5️⃣ Start ONLY the DB service first
Write-Host "`n=== Starting MySQL service (dependency) ===`n"
docker compose -f $ComposeFile up -d db
# Wait until MySQL reports healthy (max 60 s)
$maxWait = 60
$elapsed = 0
while ($elapsed -lt $maxWait) {
    $health = docker inspect --format='{{.State.Health.Status}}' $(docker compose -f $ComposeFile ps -q db) 2>$null
    if ($health -eq 'healthy') { break }
    Start-Sleep -Seconds 2
    $elapsed += 2
}
if ($health -ne 'healthy') {
    Write-Host "`n⚠️ MySQL did not become healthy after $maxWait seconds. Check its logs:" -ForegroundColor Yellow
    docker compose -f $ComposeFile logs db
    exit 1
}
Write-Host "✅ MySQL is healthy."

# -------------------------------------------------
# 6️⃣ Start the Spring back‑end (hsm-app)
Write-Host "`n=== Starting Spring back‑end (hsm-app) ===`n"
docker compose -f $ComposeFile up -d hsm-app

# -------------------------------------------------
# 7️⃣ Verify Deepgram key was loaded (wait a few seconds)
Write-Host "`n=== Waiting for hsm-app to start… ===`n"
Start-Sleep -Seconds 8
$log = docker compose -f $ComposeFile logs hsm-app 2>&1
Write-Host $log
if ($log -match 'Deepgram API key loaded') {
    Write-Host "`n✅ Deepgram API key successfully loaded." -ForegroundColor Green
} else {
    Write-Host "`n⚠️ Deepgram API key NOT loaded. Check .env and ensure DEEPGRAM_API_KEY is set." -ForegroundColor Red
    exit 1
}

# -------------------------------------------------
# 8️⃣ OPTIONAL – Run the React front‑end (npm dev server)
Write-Host "`n=== Starting React front‑end (npm run dev) ===`n"
Push-Location $FrontEndRoot
# Make sure node_modules are installed (run once if not)
if (-Not (Test-Path 'node_modules')) {
    npm ci
}
# Start dev server in a *new* background PowerShell job so the script can continue
Start-Job -ScriptBlock { npm run dev } -Name "react-dev"
Pop-Location

# -------------------------------------------------
# 9️⃣ Verify the back‑end endpoint works (simple health check)
Write-Host "`n=== Verifying back‑end health endpoint ===`n"
$healthUrl = "http://localhost:8080/actuator/health"

$health = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 10
if ($health.StatusCode -eq 200) {
    Write-Host "✅ Back‑end health endpoint OK (200)." -ForegroundColor Green
} else {
    Write-Host "⚠️ Health endpoint returned $($health.StatusCode)." -ForegroundColor Red
}


# -------------------------------------------------
# 10️⃣ Final user info
Write-Host "`n=== Deployment finished ===`n"
Write-Host " • Back‑end URL:      http://localhost:8080"
Write-Host " • Front‑end (React) URL: http://localhost:3000"
Write-Host "All logs are in $LogFile"
Write-Host "Use docker compose -f $ComposeFile logs -f to tail live logs."
Write-Host "Use Get-Job -Name react-dev | Receive-Job -Keep to see React dev server output."
