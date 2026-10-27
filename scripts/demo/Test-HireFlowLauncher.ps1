# Focused fail-closed and ownership regression tests. No database writes.
. "$PSScriptRoot\HireFlow.Common.ps1"
Initialize-HireFlowRun
$results = New-Object System.Collections.Generic.List[object]
function Check([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Rejects([scriptblock]$Action, [string]$Pattern) {
    try { & $Action; throw 'EXPECTED_REJECTION_MISSING' }
    catch { if ($_.Exception.Message -notmatch $Pattern -or $_.Exception.Message -eq 'EXPECTED_REJECTION_MISSING') { throw } }
}
function Case([string]$Name, [scriptblock]$Action) {
    try { & $Action; $results.Add(@{ name=$Name; passed=$true }); Write-Host "PASS $Name" }
    catch { $results.Add(@{ name=$Name; passed=$false; error=$_.Exception.Message }); Write-Host "FAIL $Name : $($_.Exception.Message)" }
}
Case 'PowerShell 5.1 ASCII and AST syntax' {
    foreach ($file in Get-ChildItem $PSScriptRoot -Filter '*.ps1') {
        Check (-not ([IO.File]::ReadAllBytes($file.FullName) | Where-Object { $_ -gt 127 })) "Non-ASCII script: $file"
        $tokens=$null; $errors=$null
        [Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$errors) | Out-Null
        Check ($errors.Count -eq 0) "Parser error: $file"
    }
}
Case 'Root detection independent of current directory' { Check ($script:Root -eq [IO.Path]::GetFullPath("$PSScriptRoot\..\..")) 'Wrong project root' }
Case 'Paths with spaces are quoted' { Check ((Quote-HireFlowArgument 'C:\A Project\node.exe') -eq '"C:\A Project\node.exe"') 'Argument quoting failed' }
Case 'Quotes and newlines refused' { Rejects { Quote-HireFlowArgument 'a"b' } 'Unsupported'; Rejects { Quote-HireFlowArgument "a`nb" } 'Unsupported' }
Case 'Unknown tool produces actionable failure' { Rejects { Get-HireFlowTool 'hireflow-intentionally-missing.exe' } 'Missing.*Install/configure' }
foreach ($missing in @('node.exe','java.exe','docker.exe','npm.cmd')) {
    Case "Missing $missing stops environment check" {
        $original = ${function:Get-HireFlowTool}
        try {
            function Get-HireFlowTool([string]$Name) { if ($Name -eq $missing) { throw "Missing $Name" }; & $original $Name }
            Rejects { Get-HireFlowEnvironment 'Live' } ('Missing ' + [regex]::Escape($missing))
        } finally { Set-Item Function:Get-HireFlowTool $original }
    }
}
Case 'Missing database configuration fails closed' {
    $oldRun=$script:Run; $oldPassword=$env:HIREFLOW_IT_APP_PASSWORD
    try { $script:Run="$oldRun\missing-config-test"; $env:HIREFLOW_IT_APP_PASSWORD=$null; Rejects { Get-HireFlowPassword } 'Missing isolated database credential' }
    finally { $script:Run=$oldRun; $env:HIREFLOW_IT_APP_PASSWORD=$oldPassword }
}
Case 'Lifecycle lock excludes double startup or shutdown' {
    $lock = Get-HireFlowLock
    try { Rejects { Get-HireFlowLock } 'already in progress' } finally { $lock.Dispose() }
}
Case 'Foreign backend command refused' { Rejects { Assert-HireFlowBackendIdentity ([pscustomobject]@{Name='node.exe';CommandLine='foreign'}) } 'cannot be verified' }
Case 'Host database target refused even with matching jar' {
    $process = [pscustomobject]@{ Name='java.exe'; CommandLine="$script:Root\hireflow-api\target\hireflow-api-1.0.0.jar --spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/hireflow_dev" }
    Rejects { Assert-HireFlowBackendIdentity $process } 'cannot be verified'
}
Case 'Exact isolated backend command accepted; suffixed URL rejected' {
    $process = [pscustomobject]@{ Name='java.exe'; CommandLine="$script:Root\hireflow-api\target\hireflow-api-1.0.0.jar --spring.datasource.url=$script:DbUrl --hireflow.db.url=$script:DbUrl --spring.datasource.username=hireflow_cap8_app --hireflow.db.user=hireflow_cap8_app" }
    Assert-HireFlowBackendIdentity $process
    $process.CommandLine = $process.CommandLine.Replace($script:DbUrl,$script:DbUrl+'?currentSchema=foreign')
    Rejects { Assert-HireFlowBackendIdentity $process } 'unauthorized database'
}
Case 'Unreachable HTTP health fails closed' { Check (-not (Test-HireFlowHealth 1)) 'Unreachable server reported healthy' }
Case 'Port listener is identified without termination' {
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0)
    try { $listener.Start(); $port=$listener.LocalEndpoint.Port; Check ((Get-HireFlowListener $port) -contains $PID) 'Listener owner mismatch' } finally { $listener.Stop() }
}
Case 'PID identity, stale PID, reused process and scoped shutdown' {
    $process = Start-Process powershell.exe -ArgumentList '-NoProfile -Command "Start-Sleep -Seconds 60"' -WindowStyle Hidden -PassThru
    $record = $null
    try {
        $record = New-HireFlowRecord (Get-HireFlowProcess $process.Id) 'launcher-test-child' $false
        Check (Test-HireFlowRecord $record) 'New record not recognized'
        Stop-HireFlowRecord $record
        Check (Test-HireFlowRecord $record) 'Unowned process was stopped'
        $record.owned=$true; $originalTime=$record.created; $record.created='2000-01-01T00:00:00Z'
        Stop-HireFlowRecord $record
        Check ($null -ne (Get-HireFlowProcess $process.Id)) 'Stale record killed a live process'
        $record.created=$originalTime
        Stop-HireFlowRecord $record
        Check (-not (Test-HireFlowRecord $record)) 'Owned process remained running'
    } finally { if ($record) { $record.created=$originalTime; $record.owned=$true; Stop-HireFlowRecord $record } }
}
$results | ConvertTo-Json -Depth 4 | Set-Content "$script:Run\evidence\launcher-unit.json" -Encoding UTF8
if (@($results | Where-Object { -not $_.passed }).Count) { exit 1 }
Write-Host "$($results.Count) launcher tests passed. Run real lifecycle scenarios separately."
