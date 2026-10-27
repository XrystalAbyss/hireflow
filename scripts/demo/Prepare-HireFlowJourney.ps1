param([ValidatePattern('^[A-Z0-9]{1,11}$')][string]$Round = 'VIVA22')
. "$PSScriptRoot\HireFlow.Common.ps1"
Initialize-HireFlowRun
Assert-HireFlowDatabase (Get-HireFlowPassword)
$listener = @(Get-HireFlowListener 8081)
if ($listener.Count -ne 1) { throw 'Expected one verified backend.' }
Assert-HireFlowBackendIdentity (Get-HireFlowProcess $listener[0])
if (-not (Test-HireFlowHealth)) { throw 'Backend is not healthy.' }
$config = Get-Content "$script:Run\demo.local.json" -Raw | ConvertFrom-Json
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$base = 'http://127.0.0.1:8081/api/v1'
$csrf = Invoke-RestMethod "$base/auth/csrf" -WebSession $session
$body = @{ username=$config.demoOfficerUsername; password=$config.demoOfficerPassword } | ConvertTo-Json
$profile = Invoke-RestMethod "$base/auth/login" -WebSession $session -Method Post -ContentType application/json -Headers @{'X-CSRF-TOKEN'=$csrf.token} -Body $body
try {
    if ($profile.role -notin @('PLACEMENT_OFFICER','ADMINISTRATOR')) { throw 'Officer role required.' }
    $csrf = Invoke-RestMethod "$base/auth/csrf" -WebSession $session
    $company = 'CMP-DEMO-' + $Round
    $drive = 'DRV-DEMO-' + $Round
    $interviewer = 'INT-DEMO-' + $Round
    $room = 'RM-DEMO-' + $Round
    $records = @(
        @{ endpoint='companies'; data=@{id=$company;name="Northwind Campus Demo $Round";industry='Fictional campus recruitment'} },
        @{ endpoint='interviewers'; data=@{id=$interviewer;name="Demo Technical Panel $Round";companyId=$company} },
        @{ endpoint='rooms'; data=@{id=$room;name="Demo Interview Room $Round";capacity=4} },
        @{ endpoint='drives'; data=@{id=$drive;companyId=$company;jobTitle="Graduate Software Engineer - $Round";minimumCgpa=7.5;eligibleBranches=@('CSE','IT');applicationDeadline=[DateTime]::Today.AddDays(30).ToString('yyyy-MM-dd')} }
    )
    foreach ($record in $records) {
        $url = "$base/officer/$($record.endpoint)"
        $exists = $false
        try { if ($record.endpoint -in @('interviewers','rooms')) { $items=Invoke-RestMethod $url -WebSession $session; $exists=@($items | Where-Object { $_.id -eq $record.data.id }).Count -gt 0 } else { $item=Invoke-RestMethod "$url/$($record.data.id)" -WebSession $session; $exists=$true } }
        catch { if (-not $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 404) { throw } }
        if ($exists) { Write-Host "Preserved existing $($record.data.id)."; continue }
        Invoke-RestMethod $url -WebSession $session -Method Post -ContentType application/json -Headers @{'X-CSRF-TOKEN'=$csrf.token} -Body ($record.data | ConvertTo-Json) | Out-Null
        Write-Host "Created $($record.data.id)."
    }
    @{round=$Round;driveId=$drive;companyId=$company;interviewerId=$interviewer;roomId=$room;date=[DateTime]::Today.AddDays(31).ToString('yyyy-MM-dd');studentUsername=$config.demoStudentUsername;officerUsername=$config.demoOfficerUsername} | ConvertTo-Json | Set-Content "$script:Run\evidence\prepared-journey.json" -Encoding UTF8
    Write-Host 'Fresh journey prepared. No application submitted or existing record reset. See the demo runbook.'
} finally {
    $csrf=Invoke-RestMethod "$base/auth/csrf" -WebSession $session
    Invoke-RestMethod "$base/auth/logout" -WebSession $session -Method Post -Headers @{'X-CSRF-TOKEN'=$csrf.token} | Out-Null
}