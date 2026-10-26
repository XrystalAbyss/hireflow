param([ValidateSet('Live','Simulation')][string]$Mode = 'Live')
. "$PSScriptRoot\HireFlow.Common.ps1"
try {
    Initialize-HireFlowRun
    Get-HireFlowEnvironment $Mode | Format-List
    if ($Mode -eq 'Live') { Assert-HireFlowDatabase (Get-HireFlowPassword); Write-Host 'Isolated PostgreSQL verification passed.' }
    foreach ($port in @(8081,5174,5175)) { Write-Host "Port $port listener PIDs: $((Get-HireFlowListener $port) -join ', ')" }
} catch { Write-Error $_; exit 1 }
