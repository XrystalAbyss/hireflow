# ASCII-only, Windows PowerShell 5.1 compatible. No system configuration changes.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:Root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$script:Run = Join-Path $script:Root '.run'
$script:DbUrl = 'jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it'

function Initialize-HireFlowRun {
    foreach ($folder in @($script:Run, "$script:Run\logs", "$script:Run\evidence")) {
        New-Item -ItemType Directory -Force -Path $folder | Out-Null
    }
}
function Get-HireFlowLock {
    Initialize-HireFlowRun
    try { return [IO.File]::Open("$script:Run\lifecycle.lock", 'OpenOrCreate', 'ReadWrite', 'None') }
    catch { throw 'HireFlow launch or shutdown is already in progress. Wait for that console to finish.' }
}
function Get-HireFlowTool([string]$Name) {
    $tool = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $tool) { throw "Missing $Name. Install/configure it yourself, then reopen this launcher. See docs/frontend/launcher-troubleshooting.md." }
    return $tool.Source
}
function Quote-HireFlowArgument([string]$Value) {
    if ($Value -match '["\r\n]') { throw 'Unsupported quote or newline in process argument.' }
    return '"' + $Value.TrimEnd('\') + '"'
}
function Get-HireFlowHash([string]$Value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function Get-HireFlowFileHash([string]$Path) {
    $sha = [Security.Cryptography.SHA256]::Create()
    $stream = [IO.File]::OpenRead($Path)
    try { return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '') }
    finally { $stream.Dispose(); $sha.Dispose() }
}
function Get-HireFlowProcess([int]$ProcessId) {
    return Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction SilentlyContinue
}
function New-HireFlowRecord($Process, [string]$Service, [bool]$Owned) {
    if (-not $Process -or -not $Process.ExecutablePath -or -not $Process.CommandLine) { throw 'Cannot verify service process identity.' }
    return [pscustomobject]@{
        pid = [int]$Process.ProcessId; created = $Process.CreationDate.ToUniversalTime().ToString('o')
        executable = $Process.ExecutablePath; commandHash = Get-HireFlowHash $Process.CommandLine
        service = $Service; owned = $Owned
    }
}
function Test-HireFlowRecord($Record) {
    $current = Get-HireFlowProcess $Record.pid
    return ($null -ne $current -and $null -ne $current.CommandLine -and
        $current.ExecutablePath -eq $Record.executable -and
        $current.CreationDate.ToUniversalTime().ToString('o') -eq $Record.created -and
        (Get-HireFlowHash $current.CommandLine) -eq $Record.commandHash)
}
function Save-HireFlowState($State) {
    $temp = "$script:Run\state.tmp"
    $State | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $temp -Encoding UTF8
    Move-Item -LiteralPath $temp -Destination "$script:Run\state.json" -Force
}
function Read-HireFlowState {
    if (-not (Test-Path "$script:Run\state.json")) { return $null }
    $state = Get-Content -LiteralPath "$script:Run\state.json" -Raw | ConvertFrom-Json
    if ($state.root -ne $script:Root -or $state.version -ne 1) { throw 'Runtime state belongs to another project or version. No processes were changed.' }
    return $state
}
function Get-HireFlowListener([int]$Port) {
    return @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
}
function Get-HireFlowJson([string]$Url) {
    return Invoke-RestMethod -Uri $Url -TimeoutSec 3 -UseBasicParsing
}
function Test-HireFlowHealth([int]$Port = 8081) {
    try {
        $health = Get-HireFlowJson "http://127.0.0.1:$Port/api/v1/health"
        return ($health.service -ceq 'hireflow-api' -and $health.status -ceq 'UP' -and $health.databaseStatus -ceq 'CONNECTED' -and $health.schemaStatus -ceq 'FLYWAY_VALIDATED')
    } catch { return $false }
}
function Assert-HireFlowBackendIdentity($Process) {
    $jar = Join-Path $script:Root 'hireflow-api\target\hireflow-api-1.0.0.jar'
    if (-not $Process -or $Process.Name -ne 'java.exe' -or
        $Process.CommandLine.IndexOf($jar, [StringComparison]::OrdinalIgnoreCase) -lt 0 -or
        $Process.CommandLine -notmatch [regex]::Escape("--spring.datasource.url=$script:DbUrl") -or
        $Process.CommandLine -notmatch [regex]::Escape("--hireflow.db.url=$script:DbUrl") -or
        $Process.CommandLine -notmatch '--spring.datasource.username=hireflow_cap8_app(?:["\s]|$)' -or
        $Process.CommandLine -notmatch '--hireflow.db.user=hireflow_cap8_app(?:["\s]|$)') {
        throw 'Port 8081 is occupied by a process whose HireFlow repository and isolated database identity cannot be verified. Stop it through its owner, then retry. No process was terminated.'
    }
    # Reject URL suffixes or alternate datasource targets, even if a valid prefix exists.
    foreach ($flag in @('spring.datasource.url', 'hireflow.db.url')) {
        $matchesFound = [regex]::Matches($Process.CommandLine, '--' + [regex]::Escape($flag) + '=([^"\s]+)')
        if ($matchesFound.Count -ne 1 -or $matchesFound[0].Groups[1].Value -cne $script:DbUrl) { throw 'Ambiguous or unauthorized database configuration on existing backend.' }
    }
}
function Stop-HireFlowRecord($Record) {
    if (-not $Record.owned) { Write-Host "Preserved pre-existing $($Record.service)."; return }
    if (-not (Test-HireFlowRecord $Record)) { Write-Host "Ignored stale $($Record.service) PID record."; return }
    $parent = Get-HireFlowProcess $Record.pid
    # Capture only descendants of this positively identified instance. Recheck each identity before stopping.
    $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$($Record.pid)" | Where-Object { $_.CreationDate -ge $parent.CreationDate -and $_.ExecutablePath -and $_.CommandLine })
    foreach ($child in $children) {
        Stop-HireFlowRecord (New-HireFlowRecord $child ($Record.service + '-child') $true)
    }
    if (Test-HireFlowRecord $Record) {
        Stop-Process -Id $Record.pid -ErrorAction Stop
        Wait-Process -Id $Record.pid -Timeout 10 -ErrorAction SilentlyContinue
        Write-Host "Stopped owned $($Record.service)."
    }
}
function Assert-HireFlowContainer {
    $raw = & docker inspect hireflow-cap8-postgres 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Authorized container missing. Review infra/cap8-integration; this launcher will not create or reset databases.' }
    $container = @($raw | ConvertFrom-Json)[0]
    $binding = @($container.HostConfig.PortBindings.'5432/tcp')
    $volumes = @($container.Mounts | Where-Object { $_.Destination -eq '/var/lib/postgresql/data' })
    if ($container.Config.Image -cne 'postgres:16' -or $container.Config.Labels.'com.docker.compose.project' -cne 'hireflow-cap8-it' -or
        $binding.Count -ne 1 -or $binding[0].HostIp -cne '127.0.0.1' -or $binding[0].HostPort -cne '55432' -or
        $volumes.Count -ne 1 -or $volumes[0].Name -cne 'hireflow_cap8_it_pgdata') { throw 'Isolated container identity, volume or loopback binding mismatch. Startup refused.' }
    return $container
}
function Get-HireFlowPassword {
    if ($env:HIREFLOW_IT_APP_PASSWORD) { return $env:HIREFLOW_IT_APP_PASSWORD }
    $file = "$script:Run\demo.local.json"
    if (Test-Path $file) {
        $config = Get-Content -LiteralPath $file -Raw | ConvertFrom-Json
        if ($config.databasePassword) { return [string]$config.databasePassword }
    }
    throw 'Missing isolated database credential. Set HIREFLOW_IT_APP_PASSWORD or create ignored .run/demo.local.json as described in the runbook. No default password is assumed.'
}
function Assert-HireFlowDatabase([string]$Password) {
    $null = Assert-HireFlowContainer
    $psql = Get-Command psql.exe -ErrorAction SilentlyContinue
    $psqlPath = if ($psql) { $psql.Source } else { 'C:\Program Files\PostgreSQL\16\bin\psql.exe' }
    if (-not (Test-Path $psqlPath)) { throw 'PostgreSQL psql client is missing. Install the client or add it to PATH; no server changes are needed.' }
    $previousPassword = $env:PGPASSWORD
    $previousTimeout = $env:PGCONNECT_TIMEOUT
    try {
        $env:PGPASSWORD = $Password; $env:PGCONNECT_TIMEOUT = '3'
        $sql = "SELECT current_database(),current_user,rolsuper,rolcreatedb,rolcreaterole,rolreplication,rolbypassrls,(SELECT marker_value FROM public._integration_sentinel WHERE marker_key='ENVIRONMENT_ID'),(SELECT count(*) FROM pg_extension WHERE extname='btree_gist') FROM pg_roles WHERE rolname=current_user;"
        $result = & $psqlPath -X -w -h 127.0.0.1 -p 55432 -U hireflow_cap8_app -d hireflow_cap8_it -v ON_ERROR_STOP=1 -Atc $sql 2>"$script:Run\logs\database-check.log"
        if ($LASTEXITCODE -ne 0 -or ($result -join '').Trim() -cne 'hireflow_cap8_it|hireflow_cap8_app|f|f|f|f|f|HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026|1') { throw 'Isolated database login, sentinel, extension or restricted-role verification failed. See .run/logs/database-check.log.' }
    } finally { $env:PGPASSWORD = $previousPassword; $env:PGCONNECT_TIMEOUT = $previousTimeout }
}
function Get-HireFlowEnvironment([string]$Mode, [switch]$StartDocker) {
    if (-not (Test-Path "$script:Root\pom.xml") -or -not (Test-Path "$script:Root\hireflow-app\package-lock.json")) { throw 'HireFlow project root or lockfile is missing.' }
    $node = Get-HireFlowTool 'node.exe'
    $nodeVersion = (& $node --version).TrimStart('v')
    $version = [version]$nodeVersion
    if (-not (($version.Major -eq 20 -and $version.Minor -ge 19) -or ($version.Major -eq 22 -and $version.Minor -ge 12) -or $version.Major -ge 24)) { throw "Node $version is incompatible. Use Node 20.19+, 22.12+ or a current even-numbered LTS." }
    $result = [ordered]@{ windows = [Environment]::OSVersion.VersionString; powershell = $PSVersionTable.PSVersion.ToString(); mode = $Mode; node = $node; nodeVersion = $nodeVersion; npm = Get-HireFlowTool 'npm.cmd' }
    if ($Mode -eq 'Live') {
        $result.java = Get-HireFlowTool 'java.exe'
        $javaInfo = (& $result.java --version | Out-String)
        if ($javaInfo -notmatch '(?:openjdk|java) 21\.') { throw 'Java 21 is required. Set JAVA_HOME/PATH to a JDK 21 installation.' }
        $result.javaVersion = ($javaInfo -split "`n")[0].Trim()
        $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        $result.maven = if ($maven) { $maven.Source } elseif (Test-Path "$script:Root\mvnw.cmd") { "$script:Root\mvnw.cmd" } else { throw 'Maven or the Maven wrapper is missing.' }
        $result.docker = Get-HireFlowTool 'docker.exe'
        function Test-Engine {
            $previousPreference=$ErrorActionPreference
            try { $ErrorActionPreference='Continue'; & $result.docker info --format '{{.ServerVersion}}' 1>"$script:Run\logs\docker-check.log" 2>&1; return ($LASTEXITCODE -eq 0) }
            finally { $ErrorActionPreference=$previousPreference }
        }
        $engineReady=Test-Engine
        if (-not $engineReady -and $StartDocker) {
            $desktop=Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
            if (Test-Path $desktop) {
                Write-Host 'Starting Docker Desktop normally; waiting up to 60 seconds for the engine...'
                Start-Process -FilePath $desktop -WindowStyle Hidden | Out-Null
                $deadline=[DateTime]::UtcNow.AddSeconds(60)
                do { Start-Sleep -Seconds 2; $engineReady=Test-Engine } while (-not $engineReady -and [DateTime]::UtcNow -lt $deadline)
            }
        }
        if (-not $engineReady) { throw 'Docker engine is unavailable. See .run/logs/docker-check.log. Open Docker Desktop and inspect its error. WSL mount errors require separate operator recovery; no reset, reinstall or repair is attempted.' }
    }
    return [pscustomobject]$result
}
function Get-HireFlowBuildFingerprint {
    $inputs = @("$script:Root\pom.xml", "$script:Root\hireflow-api\pom.xml") +
        @(Get-ChildItem "$script:Root\src\main", "$script:Root\hireflow-api\src\main" -File -Recurse | Select-Object -ExpandProperty FullName)
    return Get-HireFlowHash (($inputs | Sort-Object | ForEach-Object { $_ + ':' + (Get-HireFlowFileHash $_) }) -join "`n")
}
function Wait-HireFlowReady([scriptblock]$Check, $Record, [int]$Seconds, [string]$Label) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    do {
        if (-not (Test-HireFlowRecord $Record)) { throw "$Label exited during startup. See .run/logs." }
        if (& $Check) { return }
        Start-Sleep -Milliseconds 400
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Label readiness timed out after $Seconds seconds. See .run/logs."
}
