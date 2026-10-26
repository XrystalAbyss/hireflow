<#
.SYNOPSIS
    HireFlow CAP-8.5.2 Fail-Closed Database Integration Safety Guard.
.DESCRIPTION
    Validates that the database connection points EXCLUSIVELY to the authorized,
    isolated Docker integration container on 127.0.0.1:55432 with restricted role
    hireflow_cap8_app and verifies the unique sentinel marker.
    Fails closed if pointing to port 5432, superuser, or unauthorized database.
#>

param (
    [string]$HostName = "127.0.0.1",
    [int]$Port = 55432,
    [string]$Database = "hireflow_cap8_it",
    [string]$User = "hireflow_cap8_app",
    [string]$Password = $env:HIREFLOW_IT_APP_PASSWORD,
    [string]$ExpectedSentinel = "HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026"
)

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $true

# Exact allowlist: reject before connecting to any database.
if ($HostName -cne '127.0.0.1' -or $Port -ne 55432 -or $Database -cne 'hireflow_cap8_it' -or $User -cne 'hireflow_cap8_app' -or $ExpectedSentinel -cne 'HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026') {
    Write-Error 'Integration target is outside the exact authorized allowlist.'
    exit 1
}
$bindingsJson = & docker inspect hireflow-cap8-postgres --format '{{json .HostConfig.PortBindings}}'
if ($LASTEXITCODE -ne 0) { throw 'Cannot verify integration container binding' }
$bindings = $bindingsJson | ConvertFrom-Json
$published = @($bindings.'5432/tcp')
if ($published.Count -ne 1 -or $published[0].HostIp -cne '127.0.0.1' -or $published[0].HostPort -cne '55432') {
    throw 'Integration container binding mismatch'
}

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host " [SAFETY GUARD] HireFlow CAP-8.5.2 Preflight Isolation Verification" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

# 1. HARD RULE: Port 5432 is strictly prohibited for integration writes
if ($Port -eq 5432) {
    Write-Host "[FATAL GUARD REJECTION] Port 5432 detected! Direct connection to host PostgreSQL is forbidden." -ForegroundColor Red
    exit 1
}

# 2. HARD RULE: Prohibited databases
$ProhibitedDatabases = @("hireflow_dev", "hireflow_test", "aurel_dev", "aurel_test", "postgres")
if ($ProhibitedDatabases -contains $Database.ToLower()) {
    Write-Host "[FATAL GUARD REJECTION] Prohibited database '$Database' specified! Cannot run against host or shared databases." -ForegroundColor Red
    exit 1
}

# 3. Check TCP loopback reachability
Write-Host "[Guard 1/5] Checking TCP loopback reachability on ${HostName}:${Port}..." -NoNewline
$tcp = Test-NetConnection -ComputerName $HostName -Port $Port -InformationLevel Quiet
if (-not $tcp) {
    Write-Host " FAILED" -ForegroundColor Red
    Write-Host "[FATAL GUARD REJECTION] Cannot establish TCP connection to ${HostName}:${Port}. Docker container may not be running." -ForegroundColor Red
    exit 1
}
Write-Host " OK" -ForegroundColor Green

# 4. Resolve psql client path
$psqlPath = "C:\Program Files\PostgreSQL\16\bin\psql.exe"
if (-not (Test-Path $psqlPath)) {
    $psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($psqlCmd) { $psqlPath = $psqlCmd.Source }
    else {
        Write-Host "[FATAL GUARD REJECTION] psql client not found at $psqlPath or on PATH." -ForegroundColor Red
        exit 1
    }
}

# 5. Connect and verify role privilege constraints
Write-Host "[Guard 2/5] Querying role privileges for '$User'..." -NoNewline
$env:PGPASSWORD = $Password
$roleQuery = "SELECT (rolsuper OR rolreplication OR rolbypassrls), rolcreaterole, rolcreatedb FROM pg_roles WHERE rolname = current_user;"
$roleResult = & $psqlPath -h $HostName -p $Port -U $User -d $Database -t -A -c $roleQuery

if (-not $roleResult) {
    Write-Host " FAILED" -ForegroundColor Red
    Write-Host "[FATAL GUARD REJECTION] Failed to query pg_roles for user '$User'." -ForegroundColor Red
    exit 1
}

$parts = $roleResult.Trim().Split('|')
$isSuper = $parts[0] -eq 't'
$canCreateRole = $parts[1] -eq 't'
$canCreateDb = $parts[2] -eq 't'

if ($isSuper -or $canCreateRole -or $canCreateDb) {
    Write-Host " FAILED (Privilege Leak)" -ForegroundColor Red
    Write-Host "[FATAL GUARD REJECTION] Role '$User' violates least-privilege constraints! Superuser=$isSuper, CreateRole=$canCreateRole, CreateDb=$canCreateDb" -ForegroundColor Red
    exit 1
}
Write-Host " OK (Restricted Application Role Verified: NOSUPERUSER, NOCREATEDB, NOCREATEROLE)" -ForegroundColor Green

# 6. Verify database catalog name
Write-Host "[Guard 3/5] Verifying current database catalog..." -NoNewline
$currentDb = (& $psqlPath -h $HostName -p $Port -U $User -d $Database -t -A -c "SELECT current_database();").Trim()
if ($currentDb -ne $Database) {
    Write-Host " FAILED" -ForegroundColor Red
    Write-Host "[FATAL GUARD REJECTION] Connected database '$currentDb' does not match expected target '$Database'." -ForegroundColor Red
    exit 1
}
Write-Host " OK ('$currentDb')" -ForegroundColor Green

# 7. Verify Unique Integration Sentinel Marker
Write-Host "[Guard 4/5] Checking integration sentinel marker table..." -NoNewline
$sentinelValue = (& $psqlPath -h $HostName -p $Port -U $User -d $Database -t -A -c "SELECT marker_value FROM _integration_sentinel WHERE marker_key = 'ENVIRONMENT_ID';").Trim()
if ($sentinelValue -ne $ExpectedSentinel) {
    Write-Host " FAILED" -ForegroundColor Red
    Write-Host "[FATAL GUARD REJECTION] Database is missing required sentinel marker or value mismatch. Got: '$sentinelValue', Expected: '$ExpectedSentinel'" -ForegroundColor Red
    exit 1
}
Write-Host " OK ('$sentinelValue')" -ForegroundColor Green

# 8. Verify btree_gist extension presence
Write-Host "[Guard 5/5] Checking required PostgreSQL extension 'btree_gist'..." -NoNewline
$extCount = (& $psqlPath -h $HostName -p $Port -U $User -d $Database -t -A -c "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist';").Trim()
if ([int]$extCount -lt 1) {
    Write-Host " FAILED" -ForegroundColor Red
    Write-Host "[FATAL GUARD REJECTION] Extension 'btree_gist' not installed in $Database." -ForegroundColor Red
    exit 1
}
Write-Host " OK" -ForegroundColor Green

Write-Host "======================================================================" -ForegroundColor Green
Write-Host " [SAFETY GUARD PASSED] Isolated integration database is VERIFIED & SAFE." -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Green
exit 0
