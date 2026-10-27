. "$PSScriptRoot\HireFlow.Common.ps1"
$lock = $null
try {
    $lock = Get-HireFlowLock
    $state = Read-HireFlowState
    if (-not $state) { Write-Host 'No HireFlow launcher-owned services recorded.'; exit 0 }
    foreach ($record in @($state.services | Sort-Object service -Descending)) { Stop-HireFlowRecord $record }
    $state.phase = 'stopped'
    Save-HireFlowState $state
    Write-Host 'HireFlow shutdown complete. Database and logs preserved.'
} catch { Write-Error $_; exit 1 }
finally { if ($lock) { $lock.Dispose() } }
