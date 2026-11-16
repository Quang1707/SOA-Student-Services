$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Khong tim thay .env. Hay chay scripts/configure-local-secrets.ps1 truoc.'
}

$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $settings[$matches[1]] = $matches[2]
    }
}
$required = @('POSTGRES_ADMIN_USER','POSTGRES_ADMIN_PASSWORD','PROFILE_DB_NAME','PROFILE_DB_USER','PROFILE_DB_PASSWORD',
              'DOCUMENT_DB_NAME','DOCUMENT_DB_USER','DOCUMENT_DB_PASSWORD',
              'AUDIT_DB_NAME','AUDIT_DB_USER','AUDIT_DB_PASSWORD')
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace($settings[$name])) { throw "Thieu $name trong .env" }
}

# Doi PostgreSQL san sang truoc khi cap nhat role/database (toi da 60 giay).
$postgresReady = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    docker compose exec -T postgres pg_isready -U $settings['POSTGRES_ADMIN_USER'] -d postgres *> $null
    if ($LASTEXITCODE -eq 0) { $postgresReady = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $postgresReady) { throw 'PostgreSQL chua san sang. Kiem tra docker compose logs postgres.' }

$sql = @'
SELECT format('ALTER ROLE %I WITH PASSWORD %L', :'admin_user', :'admin_password')\gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'profile_user', :'profile_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'profile_user')
\gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'profile_user', :'profile_password')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'profile_db', :'profile_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'profile_db')
\gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'profile_db', :'profile_user')\gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'document_user', :'document_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'document_user')
\gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'document_user', :'document_password')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'document_db', :'document_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'document_db')
\gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'document_db', :'document_user')\gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'audit_user', :'audit_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'audit_user')
\gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'audit_user', :'audit_password')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'audit_db', :'audit_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'audit_db')
\gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'audit_db', :'audit_user')\gexec

SELECT format('GRANT ALL PRIVILEGES ON DATABASE %I TO %I', :'profile_db', :'profile_user')\gexec
SELECT format('GRANT ALL PRIVILEGES ON DATABASE %I TO %I', :'document_db', :'document_user')\gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'profile_db')\gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'document_db')\gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'audit_db')\gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'profile_db', :'profile_user')\gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'document_db', :'document_user')\gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'audit_db', :'audit_user')\gexec
'@

$sql | docker compose exec -T postgres psql -U $settings['POSTGRES_ADMIN_USER'] -d postgres -v ON_ERROR_STOP=1 `
    -v "admin_user=$($settings['POSTGRES_ADMIN_USER'])" -v "admin_password=$($settings['POSTGRES_ADMIN_PASSWORD'])" `
    -v "profile_db=$($settings['PROFILE_DB_NAME'])" -v "profile_user=$($settings['PROFILE_DB_USER'])" -v "profile_password=$($settings['PROFILE_DB_PASSWORD'])" `
    -v "document_db=$($settings['DOCUMENT_DB_NAME'])" -v "document_user=$($settings['DOCUMENT_DB_USER'])" -v "document_password=$($settings['DOCUMENT_DB_PASSWORD'])" `
    -v "audit_db=$($settings['AUDIT_DB_NAME'])" -v "audit_user=$($settings['AUDIT_DB_USER'])" -v "audit_password=$($settings['AUDIT_DB_PASSWORD'])" -f -
if ($LASTEXITCODE -ne 0) { throw 'Provision database khong thanh cong. Kiem tra POSTGRES_ADMIN_USER / volume cu / docker compose logs postgres.' }

$grantScript = @'
SELECT format('ALTER TABLE %I.%I OWNER TO %I', schemaname, tablename, :'app_user') FROM pg_tables WHERE schemaname = 'public'\gexec
SELECT format('ALTER SEQUENCE %I.%I OWNER TO %I', sequence_schema, sequence_name, :'app_user') FROM information_schema.sequences WHERE sequence_schema = 'public'\gexec
SELECT format('GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO %I', :'app_user')\gexec
SELECT format('GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO %I', :'app_user')\gexec
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'app_user')\gexec
'@
$dbSettings = @(
    [pscustomobject]@{ Name = $settings['PROFILE_DB_NAME']; User = $settings['PROFILE_DB_USER'] },
    [pscustomobject]@{ Name = $settings['DOCUMENT_DB_NAME']; User = $settings['DOCUMENT_DB_USER'] },
    [pscustomobject]@{ Name = $settings['AUDIT_DB_NAME']; User = $settings['AUDIT_DB_USER'] }
)
foreach ($db in $dbSettings) {
    $grantScript | docker compose exec -T postgres psql -U $settings['POSTGRES_ADMIN_USER'] -d $db.Name -v ON_ERROR_STOP=1 -v "app_user=$($db.User)" -f -
    if ($LASTEXITCODE -ne 0) { throw "Khong the cap quyen schema cho $($db.Name)" }
}

Write-Host 'Da cap nhat roles/databases cho volume PostgreSQL cu. Khong xoa volume hoac database.' -ForegroundColor Green
Write-Host 'Hay khoi dong lai Profile/Document service de Flyway ap dung migration.'