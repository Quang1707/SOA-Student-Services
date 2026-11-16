$ErrorActionPreference = "Stop"
$oldServerPort = $env:SERVER_PORT
$oldInstanceId = $env:EUREKA_INSTANCE_ID
$env:SERVER_PORT = "8084"
$env:EUREKA_INSTANCE_ID = "profile-service:8084"

$projectRoot = Split-Path -Parent $PSScriptRoot
$profilePath = Join-Path $projectRoot "profile-service"
Write-Host "Khoi dong PROFILE-SERVICE instance thu hai tai cong 8084."
Write-Host "Instance 1 mac dinh chay tai 8081; ca hai dung chung profile_db."
Push-Location $profilePath
try {
    & .\mvnw.cmd spring-boot:run
}
finally {
    Pop-Location
    $env:SERVER_PORT = $oldServerPort
    $env:EUREKA_INSTANCE_ID = $oldInstanceId
}
