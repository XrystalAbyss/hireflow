[CmdletBinding()]
param([switch]$NoBrowser, [ValidateRange(10,300)][int]$StartupTimeout = 90)
$Mode = 'Live'
. "$PSScriptRoot\HireFlow.Common.ps1"
$lock = $null
$success = $false
$state = $null
$started = @()
$timer = [Diagnostics.Stopwatch]::StartNew()
$environmentKeys = @('HIREFLOW_IT_APP_PASSWORD','HIREFLOW_DB_PASSWORD','HIREFLOW_DB_URL','HIREFLOW_DB_USER','VITE_HIREFLOW_MODE','HIREFLOW_LAUNCH_ID','HIREFLOW_API_PROXY')
$oldEnvironment = @{}
foreach ($key in $environmentKeys) { $oldEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }

function Start-OwnedService([string]$Executable, [string[]]$Arguments, [string]$Service, [string]$Directory) {
    $prefix = "$script:Run\logs\$($state.id)-$Service"
    $process = Start-Process -FilePath $Executable -ArgumentList (($Arguments | ForEach-Object { Quote-HireFlowArgument $_ }) -join ' ') -WorkingDirectory $Directory -WindowStyle Hidden -RedirectStandardOutput "$prefix.stdout.log" -RedirectStandardError "$prefix.stderr.log" -PassThru
    $record = New-HireFlowRecord (Get-HireFlowProcess $process.Id) $Service $true
    $script:started += $record
    $state.startupOwnedIds = @($state.startupOwnedIds) + $record.pid
    $state.services = @($state.services) + $record
    Save-HireFlowState $state
    return $record
}
function Invoke-HireFlowBuild([string]$Maven, [string[]]$Arguments, [string]$Name) {
    # CMD is only used for the Maven batch entry point; no filesystem operations cross shells.
    $command = '"' + $Maven + '" ' + (($Arguments | ForEach-Object { Quote-HireFlowArgument $_ }) -join ' ')
    $buildScript = "$script:Run\$Name.cmd"
    $exitFile = "$script:Run\$($state.id)-$Name.exit"
    @('@echo off', ('call ' + $command), 'set "HF_BUILD_EXIT=%ERRORLEVEL%"', ('> "' + $exitFile + '" echo %HF_BUILD_EXIT%'), 'exit /b %HF_BUILD_EXIT%') | Set-Content -LiteralPath $buildScript -Encoding ASCII
    $record = Start-OwnedService $env:ComSpec @('/d','/c',$buildScript) $Name $script:Root
    $process = Get-Process -Id $record.pid -ErrorAction Stop
    if (-not $process.WaitForExit(300000)) { throw "$Name exceeded five minutes. Check the build logs and dependency/network availability." }
    if (-not (Test-Path $exitFile) -or (Get-Content $exitFile -Raw).Trim() -ne '0') { throw "$Name failed. See .run/logs/$($state.id)-$Name.stdout.log." }
}
try {
    Write-Host "`nHIREFLOW | University Recruitment" -ForegroundColor Cyan
    $lock = Get-HireFlowLock
    $prior = Read-HireFlowState
    if ($prior) {
        $active = @($prior.services | Where-Object { Test-HireFlowRecord $_ })
        if ($active.Count -and $prior.mode -ne 'Live') { throw 'A legacy non-LIVE HireFlow process is still running. Use Stop-HireFlow.cmd before starting the real stack.' }
    }
    $environment = Get-HireFlowEnvironment $Mode -StartDocker
    $environment | ConvertTo-Json | Set-Content "$script:Run\evidence\environment.json" -Encoding UTF8
    Write-Host "Environment: Windows PowerShell $($environment.powershell), Node $($environment.nodeVersion)"
    $state = [pscustomobject]@{ version=1; root=$script:Root; id=[guid]::NewGuid().ToString('N'); mode=$Mode; phase='starting'; services=@(); startupOwnedIds=@(); url=''; startedAt=[DateTime]::UtcNow.ToString('o') }
    if ($prior) { $state.services = @($prior.services | Where-Object { Test-HireFlowRecord $_ }) }
    Save-HireFlowState $state
    $self = Get-HireFlowProcess $PID
    $watchArgs = @('-NoProfile','-ExecutionPolicy','Bypass','-File',"$PSScriptRoot\Watch-HireFlowStartup.ps1",'-LauncherPid',"$PID",'-LauncherCreated',$self.CreationDate.ToUniversalTime().ToString('o'),'-LaunchId',$state.id)
    Start-Process -FilePath (Get-HireFlowTool 'powershell.exe') -ArgumentList (($watchArgs | ForEach-Object { Quote-HireFlowArgument $_ }) -join ' ') -WindowStyle Hidden | Out-Null

    $app = "$script:Root\hireflow-app"
    if (-not (Test-Path "$app\node_modules\vite\bin\vite.js")) {
        Write-Host 'Installing locked frontend dependencies. Details are in .run/logs.'
        $installFile = "$script:Run\install-frontend.cmd"
        $exitFile = "$script:Run\$($state.id)-npm.exit"
        @('@echo off', ('call "' + $environment.npm + '" ci'), 'set "HF_BUILD_EXIT=%ERRORLEVEL%"', ('> "' + $exitFile + '" echo %HF_BUILD_EXIT%'), 'exit /b %HF_BUILD_EXIT%') | Set-Content $installFile -Encoding ASCII
        $record = Start-OwnedService $env:ComSpec @('/d','/c',$installFile) 'npm-ci' $app
        $installer = Get-Process -Id $record.pid
        if (-not $installer.WaitForExit(300000) -or -not (Test-Path $exitFile) -or (Get-Content $exitFile -Raw).Trim() -ne '0') { throw 'npm ci failed or timed out. See .run/logs; no system packages were installed.' }
    }
    if ($Mode -eq 'Live') {
        $env:HIREFLOW_IT_APP_PASSWORD = Get-HireFlowPassword
        $container = Assert-HireFlowContainer
        if (-not $container.State.Running) {
            Write-Host 'Starting the verified existing isolated PostgreSQL container...'
            & docker start hireflow-cap8-postgres | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Could not start the existing integration container.' }
            $deadline = [DateTime]::UtcNow.AddSeconds(30)
            do {
                $container = Assert-HireFlowContainer
                if ($container.State.Health.Status -eq 'healthy') { break }
                Start-Sleep -Milliseconds 500
            } while ([DateTime]::UtcNow -lt $deadline)
        }
        Assert-HireFlowDatabase $env:HIREFLOW_IT_APP_PASSWORD
        Write-Host 'Database: verified PostgreSQL 16, restricted role, 127.0.0.1:55432'
        $listeners = @(Get-HireFlowListener 8081)
        if ($listeners.Count) {
            if ($listeners.Count -ne 1) { throw 'Port 8081 has ambiguous listeners.' }
            $existing = Get-HireFlowProcess $listeners[0]
            Assert-HireFlowBackendIdentity $existing
            if (-not (Test-HireFlowHealth)) { throw 'Existing HireFlow backend is unhealthy. Inspect its logs; it has not been stopped.' }
            if (-not @($state.services | Where-Object { $_.pid -eq $existing.ProcessId }).Count) { $state.services += New-HireFlowRecord $existing 'backend' $false }
            Write-Host 'Backend: verified healthy existing instance reused.'
        } else {
            $jar = "$script:Root\hireflow-api\target\hireflow-api-1.0.0.jar"
            $fingerprint = Get-HireFlowBuildFingerprint
            $stamp = "$script:Run\build.json"
            $cached = if (Test-Path $stamp) { Get-Content $stamp -Raw | ConvertFrom-Json } else { $null }
            if (-not $cached -or -not (Test-Path $jar) -or $cached.source -ne $fingerprint -or $cached.jar -ne (Get-HireFlowFileHash $jar)) {
                Write-Host 'Building current Java sources (cached on subsequent launches)...'
                Invoke-HireFlowBuild $environment.maven @('-B','install','-DskipTests','-Dmaven.jar.forceCreation=true') 'build-core'
                Invoke-HireFlowBuild $environment.maven @('-B','-f','hireflow-api/pom.xml','clean','package','-DskipTests') 'build-api'
                @{ source=$fingerprint; jar=(Get-HireFlowFileHash $jar) } | ConvertTo-Json | Set-Content $stamp -Encoding UTF8
            }
            $env:HIREFLOW_DB_PASSWORD = $env:HIREFLOW_IT_APP_PASSWORD
            $env:HIREFLOW_DB_URL = $script:DbUrl; $env:HIREFLOW_DB_USER = 'hireflow_cap8_app'
            $args = @('-Duser.timezone=UTC','-jar',$jar,'--server.address=127.0.0.1','--server.port=8081',"--hireflow.db.url=$script:DbUrl",'--hireflow.db.user=hireflow_cap8_app','--hireflow.db.password=${HIREFLOW_IT_APP_PASSWORD}',"--spring.datasource.url=$script:DbUrl",'--spring.datasource.username=hireflow_cap8_app','--spring.datasource.password=${HIREFLOW_IT_APP_PASSWORD}','--hireflow.api.cors.allowed-origins=http://localhost:5174,http://127.0.0.1:5174',"--hireflow.launcher.id=$($state.id)")
            $backend = Start-OwnedService $environment.java $args 'backend' $script:Root
            $backendTimer = [Diagnostics.Stopwatch]::StartNew()
            Wait-HireFlowReady { Test-HireFlowHealth } $backend $StartupTimeout 'Backend'
            Write-Host ('Backend: ready in {0:N1}s.' -f $backendTimer.Elapsed.TotalSeconds)
        }
    }
    $port = 5174
    $env:VITE_HIREFLOW_MODE = $Mode.ToLowerInvariant()
    $env:HIREFLOW_LAUNCH_ID = $state.id
    $env:HIREFLOW_API_PROXY = 'http://127.0.0.1:8081'
    $state.url = "http://127.0.0.1:$port"
    $identityCheck = {
        try {
            $identity = Get-HireFlowJson "$($state.url)/__hireflow/launcher"
            $html = Invoke-WebRequest "$($state.url)/auth/login" -UseBasicParsing -TimeoutSec 3
            if ($identity.service -cne 'hireflow-frontend' -or $identity.root -ine $app.Replace('\','/') -or $identity.mode -ine $Mode -or $html.Content -notmatch 'HireFlow \| Campus Recruitment') { return $false }
            return (Test-HireFlowHealth $port)
        } catch { return $false }
    }
    $listeners = @(Get-HireFlowListener $port)
    if ($listeners.Count) {
        if ($listeners.Count -ne 1 -or -not (& $identityCheck)) { throw "Port $port is occupied by an unverified or different-mode service. No process was terminated." }
        $existing = Get-HireFlowProcess $listeners[0]
        if ($existing.Name -ne 'node.exe' -or $existing.CommandLine.IndexOf("$app\node_modules\vite\bin\vite.js", [StringComparison]::OrdinalIgnoreCase) -lt 0) { throw 'Frontend process does not match this repository.' }
        if (-not @($state.services | Where-Object { $_.pid -eq $existing.ProcessId }).Count) { $state.services += New-HireFlowRecord $existing 'frontend' $false }
        Write-Host 'Frontend: verified healthy existing instance reused.'
    } else {
        $frontend = Start-OwnedService $environment.node @("$app\node_modules\vite\bin\vite.js",'--host','127.0.0.1','--port',"$port",'--strictPort','--mode',('hireflow-' + $Mode.ToLowerInvariant())) 'frontend' $app
        Wait-HireFlowReady $identityCheck $frontend $StartupTimeout 'Frontend and API proxy'
        Write-Host 'Frontend and route checks: ready.'
    }
    $state.phase = 'ready'
    Save-HireFlowState $state
    $success = $true
    $timer.Stop()
    $measurement = @{ mode=$Mode; elapsedSeconds=[math]::Round($timer.Elapsed.TotalSeconds,2); at=[DateTime]::UtcNow.ToString('o'); newServices=@($started | Where-Object service -in @('backend','frontend')).Count; url=$state.url }
    $measurement | ConvertTo-Json -Compress | Add-Content "$script:Run\evidence\startup.jsonl" -Encoding UTF8
    $destination = "$($state.url)/auth/login"
    Write-Host "`nREADY: $($Mode.ToUpperInvariant()) | $destination" -ForegroundColor Green
    Write-Host ('Total startup: {0:N1}s. Logs: {1}\logs' -f $timer.Elapsed.TotalSeconds, $script:Run)
    Write-Host 'Shutdown: double-click Stop-HireFlow.cmd. Database remains running.'
    if (-not $NoBrowser) {
        try { Start-Process $destination -ErrorAction Stop | Out-Null }
        catch { Write-Warning "Browser could not open. Open $destination manually." }
    }
} catch {
    Write-Host "`nHireFlow could not start: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Location: $($_.InvocationInfo.ScriptName):$($_.InvocationInfo.ScriptLineNumber)"
    Write-Host 'Help: docs/frontend/launcher-troubleshooting.md'
} finally {
    if (-not $success -and $state) {
        foreach ($record in @($started | Sort-Object service -Descending)) { Stop-HireFlowRecord $record }
        $state.phase = 'failed'
        Save-HireFlowState $state
    }
    foreach ($key in $environmentKeys) { [Environment]::SetEnvironmentVariable($key, $oldEnvironment[$key], 'Process') }
    if ($lock) { $lock.Dispose() }
}
if (-not $success) { exit 1 }



