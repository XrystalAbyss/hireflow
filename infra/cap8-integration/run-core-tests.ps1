param([string]$Password = $env:HIREFLOW_IT_APP_PASSWORD)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
if ([string]::IsNullOrWhiteSpace($Password)) { throw 'Set HIREFLOW_IT_APP_PASSWORD to the isolated application-role password.' }
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$schema = 'cap852_core_' + [guid]::NewGuid().ToString('N').Substring(0,12)
$names = @('HIREFLOW_CORE_TEST_SCHEMA','HIREFLOW_ALLOW_ISOLATED_IT','HIREFLOW_DB_URL','HIREFLOW_DB_USER','HIREFLOW_DB_PASSWORD','HIREFLOW_IT_APP_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $env:HIREFLOW_IT_APP_PASSWORD = $Password
    & (Join-Path $PSScriptRoot 'preflight-guard.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated integration preflight failed' }
    # The bootstrap role creates only a new schema; all test connections use the restricted role.
    & docker exec hireflow-cap8-postgres psql -X -U postgres -d hireflow_cap8_it -v ON_ERROR_STOP=1 -c "CREATE SCHEMA $schema AUTHORIZATION hireflow_cap8_app;"
    if ($LASTEXITCODE -ne 0) { throw 'Disposable test schema creation failed' }
    $env:HIREFLOW_CORE_TEST_SCHEMA = $schema
    $env:HIREFLOW_ALLOW_ISOLATED_IT = '55432/hireflow_cap8_it'
    $env:HIREFLOW_DB_URL = "jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it?currentSchema=$schema,public"
    $env:HIREFLOW_DB_USER = 'hireflow_cap8_app'
    $env:HIREFLOW_DB_PASSWORD = $Password
    Push-Location $repository
    try {
        & mvn.cmd -B test '-DargLine=-Duser.timezone=UTC'
        if ($LASTEXITCODE -ne 0) { throw "Core tests failed; schema $schema retained for inspection." }
    } finally { Pop-Location }
    Write-Output "Core tests passed. Disposable schema retained: $schema"
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
