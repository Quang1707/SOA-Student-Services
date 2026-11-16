param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin01",
    [string]$Password = "DemoPassword123!",
    [ValidateRange(20, 1000)] [int]$RequestCount = 20
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd('/')

Write-Host "[1/3] Dang dang nhap qua API Gateway..."
$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" `
    -ContentType "application/json" -Body $loginBody
if (-not $login.accessToken) { throw "Khong nhan duoc accessToken tu Profile Service." }
$headers = @{ Authorization = "Bearer $($login.accessToken)" }

Write-Host "[2/3] Gui $RequestCount request tu Document Service sang Profile Service qua Eureka..."
$results = [System.Collections.Generic.List[object]]::new()
for ($i = 1; $i -le $RequestCount; $i++) {
    $info = Invoke-RestMethod -Method Get `
        -Uri "$BaseUrl/api/v1/documents/discovery/profile-instance-info" `
        -Headers $headers
    $results.Add([pscustomobject]@{
        Request = $i
        ServiceName = $info.serviceName
        InstanceId = $info.instanceId
        Port = $info.instancePort
        Result = "SUCCESS"
        Timestamp = (Get-Date).ToString("o")
    })
    Write-Host ("Request {0,2}: {1} (port {2})" -f $i, $info.instanceId, $info.instancePort)
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $projectRoot "tests\service-discovery\results"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outFile = Join-Path $outDir "load-balancing-$stamp.csv"
$results | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $outFile

Write-Host "[3/3] Tong hop ket qua:"
$summary = $results | Group-Object InstanceId | Sort-Object Name
$summary | ForEach-Object { Write-Host ("{0}: {1} request" -f $_.Name, $_.Count) }
Write-Host "CSV: $outFile"

if ($summary.Count -lt 2) {
    Write-Warning "Chua thay request toi tu hai instance. Kiem tra Eureka Dashboard, doi registry cap nhat va chay lai."
    exit 2
}
Write-Host "PASS: Consumer da nhan phan hoi tu $($summary.Count) instance Profile Service." -ForegroundColor Green
