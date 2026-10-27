param(
    [string]$Password = $env:HIREFLOW_IT_APP_PASSWORD,
    [string]$TestFilter = ''
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($Password)) {
    $localProps = Join-Path $repository 'hireflow-local.properties'
    if (Test-Path $localProps) {
        Get-Content $localProps | ForEach-Object {
            if ($_ -match '^\s*hireflow\.db\.password\s*=\s*(.+)$') {
                $Password = $matches[1].Trim()
            }
        }
    }
}
if ([string]::IsNullOrWhiteSpace($Password)) {
    throw 'Set HIREFLOW_IT_APP_PASSWORD or pass -Password for the isolated application-role password.'
}
$apiDir = Join-Path $repository 'hireflow-api'
$names = @('HIREFLOW_ALLOW_ISOLATED_IT','HIREFLOW_DB_URL','HIREFLOW_DB_USER','HIREFLOW_DB_PASSWORD','HIREFLOW_IT_APP_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $env:HIREFLOW_IT_APP_PASSWORD = $Password
    & (Join-Path $PSScriptRoot 'preflight-guard.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated integration preflight failed' }

    $env:HIREFLOW_ALLOW_ISOLATED_IT = '55432/hireflow_cap8_it'
    $env:HIREFLOW_DB_URL = 'jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it'
    $env:HIREFLOW_DB_USER = 'hireflow_cap8_app'
    $env:HIREFLOW_DB_PASSWORD = $Password

    Push-Location $apiDir
    try {
        $mvnArgs = @('-B', 'test', '-DargLine=-Duser.timezone=UTC')
        if (-not [string]::IsNullOrWhiteSpace($TestFilter)) {
            $mvnArgs += "-Dtest=$TestFilter"
        }
        & mvn.cmd @mvnArgs
        if ($LASTEXITCODE -ne 0) { throw 'API tests failed' }
    } finally { Pop-Location }
    Write-Output 'API tests passed.'
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
