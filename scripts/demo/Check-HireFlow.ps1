# Read-only HireFlow demo readiness check. Verifies environment, isolation, services,
# accounts and a real authenticated login. Login checks update normal authentication audit timestamps.
# ASCII-only, Windows PowerShell 5.1 compatible.
[CmdletBinding()]
param()
. "$PSScriptRoot\HireFlow.Common.ps1"

$script:Failures = 0
$script:Psql = $null
$databaseVerified = $false
$backendVerified = $false
$base = 'http://127.0.0.1:8081'

function Write-HireFlowStep([string]$Label, [bool]$Passed, [string]$Detail) {
    if ($Passed) {
        Write-Host ("[PASS] {0}{1}" -f $Label, $(if ($Detail) { " - $Detail" } else { '' })) -ForegroundColor Green
    } else {
        $script:Failures++
        Write-Host ("[FAIL] {0}{1}" -f $Label, $(if ($Detail) { " - $Detail" } else { '' })) -ForegroundColor Red
    }
}

function Get-HireFlowPsqlPath {
    $psql = Get-Command psql.exe -ErrorAction SilentlyContinue
    if ($psql) { return $psql.Source }
    $fallback = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
    if (Test-Path $fallback) { return $fallback }
    throw 'psql client not found.'
}

function Get-HireFlowCsrf([Microsoft.PowerShell.Commands.WebRequestSession]$Session) {
    return Invoke-RestMethod -Uri "$base/api/v1/auth/csrf" -Method Get -WebSession $Session -TimeoutSec 5
}

Write-Host ''
Write-Host 'HIREFLOW - DEMO CONTROL CENTER' -ForegroundColor Cyan
Write-Host 'Checks preserve recruitment records; logins update normal authentication audit timestamps.' -ForegroundColor DarkGray

# 1. Java 21 -------------------------------------------------------------------
try {
    $java = Get-HireFlowTool 'java.exe'
    $info = (& $java --version | Out-String)
    Write-HireFlowStep 'Java 21' ($info -match '(?:openjdk|java) 21\.') (($info -split "`n")[0].Trim())
} catch { Write-HireFlowStep 'Java 21' $false $_.Exception.Message }

# 2. Node.js -------------------------------------------------------------------
try {
    $node = Get-HireFlowTool 'node.exe'
    $nodeVersion = (& $node --version).TrimStart('v')
    $version = [version]$nodeVersion
    $ok = (($version.Major -eq 20 -and $version.Minor -ge 19) -or ($version.Major -eq 22 -and $version.Minor -ge 12) -or $version.Major -ge 24)
    Write-HireFlowStep 'Node.js' $ok "v$nodeVersion"
} catch { Write-HireFlowStep 'Node.js' $false $_.Exception.Message }

# 3. Docker engine -------------------------------------------------------------
try {
    $docker = Get-HireFlowTool 'docker.exe'
    $server = (& $docker info --format '{{.ServerVersion}}' 2>$null)
    Write-HireFlowStep 'Docker engine' ($LASTEXITCODE -eq 0) $server
} catch { Write-HireFlowStep 'Docker engine' $false $_.Exception.Message }

# 4. Isolated container identity ----------------------------------------------
try {
    $container = Assert-HireFlowContainer
    $running = $container.State.Running -and $container.State.Health.Status -eq 'healthy'
    Write-HireFlowStep 'Isolated PostgreSQL container' $running "hireflow-cap8-postgres $($container.State.Status)"
} catch { Write-HireFlowStep 'Isolated PostgreSQL container' $false $_.Exception.Message }

# 5. Database isolation (fail-closed guard) -----------------------------------
$dbPassword = $null
try {
    $dbPassword = Get-HireFlowPassword
    $script:Psql = Get-HireFlowPsqlPath
    Assert-HireFlowDatabase $dbPassword
    $databaseVerified=$true
    Write-HireFlowStep 'Database isolation' $true '127.0.0.1:55432/hireflow_cap8_it, restricted role, sentinel'
} catch { Write-HireFlowStep 'Database isolation' $false $_.Exception.Message }

# 6. Backend health ------------------------------------------------------------
try {
    $listeners=@(Get-HireFlowListener 8081)
    if ($listeners.Count -ne 1) { throw 'No unambiguous backend listener.' }
    Assert-HireFlowBackendIdentity (Get-HireFlowProcess $listeners[0])
    $backendVerified=Test-HireFlowHealth
    Write-HireFlowStep 'Spring Boot API identity and health' $backendVerified "$base/api/v1/health"
} catch { Write-HireFlowStep 'Spring Boot API identity and health' $false $_.Exception.Message }

# 7. Frontend readiness --------------------------------------------------------
$frontendPort = $null
foreach ($candidate in @(5174, 5175)) {
    try {
        $identity = Invoke-RestMethod -Uri "http://127.0.0.1:$candidate/__hireflow/launcher" -TimeoutSec 4 -UseBasicParsing
        if ($identity.service -ceq 'hireflow-frontend' -and $identity.mode -ceq 'live' -and $identity.root -ieq (Join-Path $script:Root 'hireflow-app').Replace('\','/')) { $frontendPort = $candidate; break }
    } catch { }
}
if ($frontendPort) {
    Write-HireFlowStep 'React frontend' $true "http://127.0.0.1:$frontendPort"
} else {
    Write-HireFlowStep 'React frontend' $false 'No verified HireFlow frontend listener on 5174 or 5175. Run Launch-HireFlow.cmd.'
}

# 8. Demo accounts present -----------------------------------------------------
if ($databaseVerified -and $dbPassword -and $script:Psql) {
    try {
        $previous = $env:PGPASSWORD
        $env:PGPASSWORD = $dbPassword
        $accounts = (& $script:Psql -X -w -h 127.0.0.1 -p 55432 -U hireflow_cap8_app -d hireflow_cap8_it -Atc "SELECT username||'|'||role FROM user_accounts WHERE username IN ('demo.master','demo.officer','demo.student') ORDER BY username;")
        $env:PGPASSWORD = $previous
        $count = @($accounts | Where-Object { $_ }).Count
        Write-HireFlowStep 'Demo accounts provisioned' ($count -eq 3) (($accounts | Where-Object { $_ }) -join ', ')
    } catch { Write-HireFlowStep 'Demo accounts provisioned' $false $_.Exception.Message }
} else {
    Write-HireFlowStep 'Demo accounts provisioned' $false 'Cannot query accounts without database isolation.'
}

# 9. Real authenticated login (demo.master) ------------------------------------
$credentialFile = "$script:Run\demo.local.json"
if (-not $databaseVerified -or -not $backendVerified) { Write-HireFlowStep 'Authenticated checks' $false 'Skipped: backend identity or isolated database verification failed.' } elseif (Test-Path $credentialFile) {
    $credentials = Get-Content -LiteralPath $credentialFile -Raw | ConvertFrom-Json
    $hasDemo = ($credentials.PSObject.Properties['demoMasterUsername'] -and $credentials.PSObject.Properties['demoMasterPassword'])
    if (-not $hasDemo) {
        Write-HireFlowStep 'Demo credential file' $false 'Credential file has no demo account entries. Run Setup-HireFlow-Demo.cmd.'
    } else {
        try {
            $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
            $csrf = Get-HireFlowCsrf $session
            $body = @{ username = $credentials.demoMasterUsername; password = $credentials.demoMasterPassword } | ConvertTo-Json
            $profile = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -WebSession $session -ContentType 'application/json' -Headers @{ 'X-CSRF-TOKEN' = $csrf.token } -Body $body -TimeoutSec 8
            Write-HireFlowStep 'demo.master login' ($profile.role -ceq 'ADMINISTRATOR') "role=$($profile.role)"

            $me = Invoke-RestMethod -Uri "$base/api/v1/auth/me" -Method Get -WebSession $session -TimeoutSec 8
            Write-HireFlowStep 'demo.master /auth/me' ($me.role -ceq 'ADMINISTRATOR') "role=$($me.role)"

            $rooms = Invoke-RestMethod -Uri "$base/api/v1/officer/rooms" -Method Get -WebSession $session -TimeoutSec 8
            Write-HireFlowStep 'Administrator officer-portal access' ($null -ne $rooms) "$(@($rooms).Count) room record(s) readable"

            $csrf2 = Get-HireFlowCsrf $session
            $logout = Invoke-RestMethod -Uri "$base/api/v1/auth/logout" -Method Post -WebSession $session -ContentType 'application/json' -Headers @{ 'X-CSRF-TOKEN' = $csrf2.token } -TimeoutSec 8
            Write-HireFlowStep 'Logout' ($logout.message -match 'Logged out') $logout.message
        } catch { Write-HireFlowStep 'demo.master authenticated session' $false $_.Exception.Message }

        try {
            $badSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
            $badCsrf = Get-HireFlowCsrf $badSession
            $badBody = @{ username = $credentials.demoMasterUsername; password = 'definitely-not-the-password' } | ConvertTo-Json
            Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -WebSession $badSession -ContentType 'application/json' -Headers @{ 'X-CSRF-TOKEN' = $badCsrf.token } -Body $badBody -TimeoutSec 8 | Out-Null
            Write-HireFlowStep 'Invalid password rejected' $false 'An invalid password was unexpectedly accepted.'
        } catch {
            $status = $null
            try { $status = [int]$_.Exception.Response.StatusCode } catch { }
            Write-HireFlowStep 'Invalid password rejected' ($status -eq 401) "HTTP $status"
        }
    }
} else {
    Write-HireFlowStep 'Demo credential file' $false 'Missing .run/demo.local.json. Run Setup-HireFlow-Demo.cmd first.'
}

Write-Host ''
if ($script:Failures -eq 0) {
    Write-Host 'HIREFLOW DEMO READY - all critical checks passed.' -ForegroundColor Green
} else {
    Write-Host "HIREFLOW DEMO NOT READY - $script:Failures critical check(s) failed." -ForegroundColor Red
    Write-Host 'Start with Launch-HireFlow.cmd, then Setup-HireFlow-Demo.cmd, and re-run this check.'
    exit 1
}
