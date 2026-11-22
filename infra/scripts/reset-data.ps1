# Reset the LOCAL database to a fresh start with sample data (Windows PowerShell).
#   powershell -File infra\scripts\reset-data.ps1        (asks first)
#   powershell -File infra\scripts\reset-data.ps1 -Yes   (no question)
#
# What it does: empties the local database, then restarts the backend. On start the backend
# re-creates every table (Flyway) and loads the sample data (sample data loader, from P1-01).
# Only touches the local Docker Compose database (project "gemshop") - never a server.
param([switch]$Yes)
# Docker writes progress to stderr; failures are detected with $LASTEXITCODE instead.
$ErrorActionPreference = "Continue"

Set-Location (Join-Path $PSScriptRoot "..\..")
$dbUser = if ($env:DB_USER) { $env:DB_USER } else { "gemshop" }
$dbName = if ($env:DB_NAME) { $env:DB_NAME } else { "gemshop" }
$healthUrl = "http://127.0.0.1:8081/api/health"

if (-not $Yes) {
    $answer = Read-Host "This deletes ALL data in the local database ($dbName) and loads fresh sample data. Continue? [y/N]"
    if ($answer -notmatch '^(y|yes)$') { Write-Host "Cancelled."; exit 1 }
}

function Invoke-Step([string]$Text, [scriptblock]$Command) {
    Write-Host $Text
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "Failed: $Text" }
}

Invoke-Step "1/4 Starting the local database..." { docker compose up -d --wait db }
Write-Host "2/4 Stopping the backend..."
docker compose stop server 2>&1 | Out-Null
Invoke-Step "3/4 Emptying the database..." {
    docker compose exec -T db psql -v ON_ERROR_STOP=1 -q -U $dbUser -d $dbName `
        -c "drop schema public cascade; create schema public authorization `"$dbUser`";"
}
Invoke-Step "   ...and the uploaded files" {
    docker compose run --rm --no-deps -T --user root --entrypoint sh server -c "rm -rf /data/files/*" | Out-Null
}
Invoke-Step "4/4 Starting the backend (tables + sample data)..." { docker compose up -d server }

for ($i = 0; $i -lt 60; $i++) {
    try {
        $health = Invoke-RestMethod -UseBasicParsing -Uri $healthUrl -TimeoutSec 5
        Write-Host "Done. Fresh local data is ready - API $($health.status), database $($health.database)."
        exit 0
    } catch { Start-Sleep -Seconds 2 }
}
Write-Error "The backend did not start in 2 minutes. Check: docker compose logs server"
exit 1
