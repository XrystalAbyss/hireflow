param([int]$LauncherPid, [string]$LauncherCreated, [string]$LaunchId)
. "$PSScriptRoot\HireFlow.Common.ps1"
# A short-lived guard handles a console being closed during startup, when finally cannot run.
while ($true) {
    $owner = Get-HireFlowProcess $LauncherPid
    if (-not $owner -or $owner.CreationDate.ToUniversalTime().ToString('o') -ne $LauncherCreated) { break }
    $state = Read-HireFlowState
    if ($state -and ($state.id -ne $LaunchId -or $state.phase -ne 'starting')) { exit 0 }
    Start-Sleep -Seconds 1
}
$lock = $null
try {
    $lock = Get-HireFlowLock
    $state = Read-HireFlowState
    if ($state -and $state.id -eq $LaunchId -and $state.phase -eq 'starting') {
        foreach ($record in @($state.services | Where-Object { $_.pid -in $state.startupOwnedIds })) { Stop-HireFlowRecord $record }
        $state.phase = 'interrupted'
        Save-HireFlowState $state
    }
} finally { if ($lock) { $lock.Dispose() } }
