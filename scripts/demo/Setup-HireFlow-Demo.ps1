# One-time, secret-safe demo provisioning for the isolated HireFlow demo database.
# ASCII-only, Windows PowerShell 5.1 compatible. No system configuration changes.
[CmdletBinding()]
param(
    [switch]$NonInteractive,
    [switch]$SkipAccounts,
    [switch]$SkipDataset,
    [switch]$ResetScenario
)
. "$PSScriptRoot\HireFlow.Common.ps1"

function New-HireFlowDemoPassword {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 24
    $rng.GetBytes($bytes)
    return -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
}

function ConvertFrom-HireFlowSecure([Security.SecureString]$Secure) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Get-HireFlowPsql {
    $psql = Get-Command psql.exe -ErrorAction SilentlyContinue
    if ($psql) { return $psql.Source }
    $fallback = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
    if (Test-Path $fallback) { return $fallback }
    throw 'PostgreSQL psql client is missing. Install the client or add it to PATH.'
}

function Invoke-HireFlowSqlFile([string]$Path) {
    Write-Host "Applying $(Split-Path -Leaf $Path) to the isolated demo database..."
    $previous = $env:PGPASSWORD
    $env:PGPASSWORD = $script:DbPassword
    try {
        & $script:Psql -X -w -h 127.0.0.1 -p 55432 -U hireflow_cap8_app -d hireflow_cap8_it -v ON_ERROR_STOP=1 -f $Path
        if ($LASTEXITCODE -ne 0) { throw "SQL step failed: $(Split-Path -Leaf $Path)" }
    } finally {
        $env:PGPASSWORD = $previous
    }
}

function Resolve-HireFlowDemoSecret([string]$Name, [string]$Existing, [bool]$GenerateIfMissing) {
    $fromEnv = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ($fromEnv) { return $fromEnv }
    if ($Existing) { return $Existing }
    if ($GenerateIfMissing) { return (New-HireFlowDemoPassword) }
    if ($script:Interactive) {
        $secure = Read-Host -Prompt "Enter password for $($Name.Replace('HIREFLOW_DEMO_','').Replace('_PASSWORD',''))" -AsSecureString
        $value = ConvertFrom-HireFlowSecure $secure
        if ([string]::IsNullOrEmpty($value)) { throw "A non-empty password is required for $Name." }
        return $value
    }
    return $null
}

$script:Interactive = -not $NonInteractive
$script:Psql = $null
$credentialFile = "$script:Run\demo.local.json"
$touchedSecrets = $false
try {
    Initialize-HireFlowRun
    Write-Host ''
    Write-Host 'HIREFLOW - DEMO SETUP' -ForegroundColor Cyan
    Write-Host 'Isolated demo database provisioning. No shared database is contacted.' -ForegroundColor DarkGray

    $environment = Get-HireFlowEnvironment 'Live'
    Write-Host ("[PASS] Java 21, Node {0}, Docker engine present" -f $environment.nodeVersion) -ForegroundColor Green
    $script:Psql = Get-HireFlowPsql

    $dbPassword = Get-HireFlowPassword
    $script:DbPassword = $dbPassword
    $env:HIREFLOW_IT_APP_PASSWORD = $dbPassword
    & "$script:Root\infra\cap8-integration\preflight-guard.ps1" -Password $dbPassword
    if ($LASTEXITCODE -ne 0) { throw 'Isolation preflight failed. Provisioning refused.' }
    Assert-HireFlowDatabase $dbPassword
    Write-Host '[PASS] Isolated database verified (127.0.0.1:55432/hireflow_cap8_it, restricted role, sentinel)' -ForegroundColor Green

    $existing = if (Test-Path $credentialFile) { Get-Content -LiteralPath $credentialFile -Raw | ConvertFrom-Json } else { $null }
    function Read-HireFlowExisting([string]$Name) {
        if ($existing -and $existing.PSObject.Properties[$Name]) { return $existing.$Name }
        return $null
    }

    if ($ResetScenario) {
        if ($script:Interactive) {
            $confirm = Read-Host 'This deletes ONLY explicitly owned scenario records in the isolated database. Type RESET to continue'
            if ($confirm -cne 'RESET') { Write-Host 'Reset cancelled.'; exit 0 }
        }
        Invoke-HireFlowSqlFile "$script:Root\infra\cap8-integration\university-season-reset.sql"
        Invoke-HireFlowSqlFile "$script:Root\infra\cap8-integration\demo-scenario-reset.sql"
        Write-Host '[PASS] Demo and university scenario reset (exact manifest records removed, VIVA22 preserved)' -ForegroundColor Green
        if ($SkipAccounts -and $SkipDataset) { exit 0 }
    }

    $masterPassword = $null
    $officerPassword = $null
    $studentPassword = $null

    if (-not $SkipAccounts) {
        $masterPassword = Resolve-HireFlowDemoSecret 'HIREFLOW_DEMO_MASTER_PASSWORD' (Read-HireFlowExisting 'demoMasterPassword') $false
        if (-not $masterPassword) { throw 'A demo.master password is required. Set HIREFLOW_DEMO_MASTER_PASSWORD or run interactively.' }
        $officerPassword = Resolve-HireFlowDemoSecret 'HIREFLOW_DEMO_OFFICER_PASSWORD' (Read-HireFlowExisting 'demoOfficerPassword') $true
        $studentPassword = Resolve-HireFlowDemoSecret 'HIREFLOW_DEMO_STUDENT_PASSWORD' (Read-HireFlowExisting 'demoStudentPassword') $true

        $classes = "$script:Root\target\classes\com\hireflow\web\security\PasswordHasher.class"
        if (-not (Test-Path $classes)) {
            Write-Host 'Core classes are missing. Build them first with Launch-HireFlow.cmd, then re-run this setup.'
            throw 'Missing target/classes build output for the password hashing service.'
        }
        $bcrypt = "$env:USERPROFILE\.m2\repository\at\favre\lib\bcrypt\0.10.2\bcrypt-0.10.2.jar"
        $bytes = "$env:USERPROFILE\.m2\repository\at\favre\lib\bytes\1.5.0\bytes-1.5.0.jar"
        $postgres = "$env:USERPROFILE\.m2\repository\org\postgresql\postgresql\42.7.3\postgresql-42.7.3.jar"
        if (-not (Test-Path $bcrypt) -or -not (Test-Path $bytes) -or -not (Test-Path $postgres)) { throw 'Required local Maven dependencies (bcrypt/bytes/postgresql) were not found.' }

        $env:HIREFLOW_DEMO_MASTER_PASSWORD = $masterPassword
        $env:HIREFLOW_DEMO_OFFICER_PASSWORD = $officerPassword
        $env:HIREFLOW_DEMO_STUDENT_PASSWORD = $studentPassword
        $touchedSecrets = $true
        try {
            $classpath = "$script:Root\target\classes;$bcrypt;$bytes;$postgres"
            & $environment.java '-Duser.timezone=UTC' -cp $classpath "$script:Root\infra\cap8-integration\ProvisionDemoAccounts.java"
            if ($LASTEXITCODE -ne 0) { throw 'Demo account provisioning failed.' }
        } finally {
            $env:HIREFLOW_DEMO_MASTER_PASSWORD = $null
            $env:HIREFLOW_DEMO_OFFICER_PASSWORD = $null
            $env:HIREFLOW_DEMO_STUDENT_PASSWORD = $null
        }
        Write-Host '[PASS] demo.master (ADMINISTRATOR), demo.officer (PLACEMENT_OFFICER), demo.student (STUDENT) provisioned as BCrypt hashes' -ForegroundColor Green
    }

    if (-not $SkipDataset) {
        Invoke-HireFlowSqlFile "$script:Root\infra\cap8-integration\demo-scenario.sql"
        Invoke-HireFlowSqlFile "$script:Root\infra\cap8-integration\university-season-scenario.sql"
        Write-Host '[PASS] Fictional university recruitment scenario and K/L room inventory seeded (idempotent, no duplicates)' -ForegroundColor Green
    }

    # Merge into the existing gitignored credential file so unrelated entries survive.
    $summary = [ordered]@{}
    if ($existing) { foreach ($property in $existing.PSObject.Properties) { $summary[$property.Name] = $property.Value } }
    $summary['databasePassword'] = $dbPassword
    $summary['database'] = 'hireflow_cap8_it (127.0.0.1:55432)'
    $summary['demoMasterUsername'] = 'demo.master'
    $summary['demoMasterRole'] = 'ADMINISTRATOR'
    $summary['demoOfficerUsername'] = 'demo.officer'
    $summary['demoStudentUsername'] = 'demo.student'
    $summary['demoStudentRecordId'] = 'DEMO-STU-01'
    if ($masterPassword) { $summary['demoMasterPassword'] = $masterPassword }
    if ($officerPassword) { $summary['demoOfficerPassword'] = $officerPassword }
    if ($studentPassword) { $summary['demoStudentPassword'] = $studentPassword }
    $summary | ConvertTo-Json | Set-Content -LiteralPath $credentialFile -Encoding UTF8
    Write-Host ''
    Write-Host 'Credentials written to the gitignored file .run/demo.local.json (never committed).' -ForegroundColor Yellow
    Write-Host 'Accounts: demo.master, demo.officer, demo.student. Passwords are intentionally not printed here.'
    Write-Host 'Run Check-HireFlow.cmd to verify login before demonstrating.' -ForegroundColor Cyan
} catch {
    Write-Host ''
    Write-Host "Demo setup failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    $env:HIREFLOW_IT_APP_PASSWORD = $null
    $env:PGPASSWORD = $null
    if ($touchedSecrets) {
        $env:HIREFLOW_DEMO_MASTER_PASSWORD = $null
        $env:HIREFLOW_DEMO_OFFICER_PASSWORD = $null
        $env:HIREFLOW_DEMO_STUDENT_PASSWORD = $null
    }
}
