$ErrorActionPreference = 'Stop'

function New-RandomSecret {
    param([int]$ByteCount = 48, [switch]$Hex)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $bytes = New-Object byte[] $ByteCount
        $rng.GetBytes($bytes)
        if ($Hex) { return (([BitConverter]::ToString($bytes)) -replace '-', '') }
        return [Convert]::ToBase64String($bytes)
    }
    finally { $rng.Dispose() }
}

function Get-OrCreateUserSecret {
    param([string]$Name, [int]$ByteCount = 48, [switch]$Hex)
    $value = [Environment]::GetEnvironmentVariable($Name, 'User')
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    }
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Length -lt 32) {
        $value = New-RandomSecret -ByteCount $ByteCount -Hex:$Hex
    }
    [Environment]::SetEnvironmentVariable($Name, $value, 'User')
    [Environment]::SetEnvironmentVariable($Name, $value, 'Process')
    return $value
}

$root = Split-Path -Parent $PSScriptRoot
$settings = [ordered]@{
    POSTGRES_ADMIN_USER = 'postgres'
    POSTGRES_ADMIN_PASSWORD = (Get-OrCreateUserSecret 'POSTGRES_ADMIN_PASSWORD' 32 -Hex)
    PROFILE_DB_NAME = 'profile_db'
    PROFILE_DB_USER = 'profile_app'
    PROFILE_DB_PASSWORD = (Get-OrCreateUserSecret 'PROFILE_DB_PASSWORD' 32 -Hex)
    DOCUMENT_DB_NAME = 'document_db'
    DOCUMENT_DB_USER = 'document_app'
    DOCUMENT_DB_PASSWORD = (Get-OrCreateUserSecret 'DOCUMENT_DB_PASSWORD' 32 -Hex)
    AUDIT_DB_NAME = 'audit_db'
    AUDIT_DB_USER = 'audit_app'
    AUDIT_DB_PASSWORD = (Get-OrCreateUserSecret 'AUDIT_DB_PASSWORD' 32 -Hex)
    RABBITMQ_USER = 'student_app'
    RABBITMQ_PASSWORD = (Get-OrCreateUserSecret 'RABBITMQ_PASSWORD' 32 -Hex)
    JWT_SECRET = (Get-OrCreateUserSecret 'JWT_SECRET' 48)
    JWT_EXPIRATION_SECONDS = '86400'
    JWT_ISSUER = 'student-profile-auth-service'
    INTERNAL_SERVICE_KEY = (Get-OrCreateUserSecret 'INTERNAL_SERVICE_KEY' 48 -Hex)
}

# Fixed identifiers are also available to local IDE-launched services.
foreach ($name in @('PROFILE_DB_NAME','PROFILE_DB_USER','DOCUMENT_DB_NAME','DOCUMENT_DB_USER','AUDIT_DB_NAME','AUDIT_DB_USER','RABBITMQ_USER','JWT_EXPIRATION_SECONDS','JWT_ISSUER')) {
    $value = $settings[$name]
    [Environment]::SetEnvironmentVariable($name, $value, 'User')
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

$envPath = Join-Path $root '.env'
$lines = foreach ($entry in $settings.GetEnumerator()) { "{0}={1}" -f $entry.Key, $entry.Value }
Set-Content -LiteralPath $envPath -Value $lines -Encoding ascii

Write-Host 'Da tao/cap nhat .env va cau hinh secrets cho tai khoan Windows hien tai.' -ForegroundColor Green
Write-Host 'Gia tri bi mat khong duoc hien thi. Hay dong/mo lai terminal va IDE.'
Write-Host 'Khong commit .env, khong gui .env va khong dua cac gia tri nay vao bao cao.'
Write-Host 'Neu dang dung volume PostgreSQL cu, khong xoa volume. Hay doc muc nang cap database trong README.'
