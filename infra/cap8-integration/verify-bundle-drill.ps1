$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $true

$bundle = "C:\Users\astra\Desktop\Project\HireFlow-Recovery\git_bundles\hireflow_cap864_all_refs.bundle"
if (-not (Test-Path $bundle)) {
    throw "Target bundle does not exist: $bundle"
}

Write-Host "Step 1: Validating Git bundle integrity at $bundle..."
& git bundle verify $bundle
if ($LASTEXITCODE -ne 0) { throw "Git bundle verification failed." }

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# Generate unique temporary directory name specifically owned by this execution
$executionId = [System.Guid]::NewGuid().ToString('N').Substring(0, 12)
$drillParent = Join-Path $env:TEMP "hireflow_recovery_drills"
if (-not (Test-Path $drillParent)) {
    New-Item -ItemType Directory -Path $drillParent -Force | Out-Null
}
$drillDir = Join-Path $drillParent "drill_$executionId"

# Safety Guard 1: Verify target directory does NOT already exist
if (Test-Path $drillDir) {
    throw "Safety violation: Target drill directory already exists ($drillDir). Aborting to prevent accidental overwrites."
}

# Safety Guard 2: Verify the restoration target is strictly outside the primary repository
$resolvedDrill = [System.IO.Path]::GetFullPath($drillDir)
$resolvedRepo = [System.IO.Path]::GetFullPath($repoRoot)
if ($resolvedDrill.StartsWith($resolvedRepo, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Safety violation: Drill directory ($resolvedDrill) must be outside the primary repository ($resolvedRepo)."
}

Write-Host "Step 2: Cloning from offline bundle into verified unique target: $drillDir..."
& git clone $bundle $drillDir
if ($LASTEXITCODE -ne 0) { throw "Git clone from bundle failed." }

# Safety Guard 3: Drop an exclusive ownership token inside the cloned directory
$ownershipToken = "OWNER_HIREFLOW_DRILL_$executionId"
$tokenFile = Join-Path $drillDir ".drill_ownership_token"
Set-Content -Path $tokenFile -Value $ownershipToken

$drillSuccess = $false
try {
    Push-Location $drillDir

    # Step 3: Checkout develop/stage5
    & git checkout develop/stage5
    $log = (& git log -1 --oneline)
    Write-Host "Restored commit HEAD: $log"

    # Step 4: Verify the preserved checkpoint tags
    $expectedTags = @(
        'v0.8.6.2-prototype-snapshot',
        'v0.8.6.3-university-season-snapshot',
        'v0.8.6.4-final-academic-prototype'
    )
    $restoredTags = (& git tag -l)
    foreach ($tag in $expectedTags) {
        if ($restoredTags -notcontains $tag) {
            throw "Preserved checkpoint tag '$tag' missing from restored bundle tags."
        }
    }
    Write-Host "Verified preserved checkpoint tags present: $($expectedTags -join ', ')"

    # Step 5: Verify restored source files integrity
    $requiredFiles = @(
        'pom.xml',
        'hireflow-api\pom.xml',
        'hireflow-app\package.json',
        'src\main\java\com\hireflow\scheduler\GreedyScheduler.java',
        'infra\cap8-integration\university-season-scenario.sql'
    )
    foreach ($file in $requiredFiles) {
        $filePath = Join-Path $drillDir $file
        if (-not (Test-Path $filePath)) {
            throw "Essential project file '$file' missing in restored working tree."
        }
    }
    Write-Host "Verified essential project source files present in restored working tree."

    $drillSuccess = $true
} finally {
    Pop-Location

    # Step 6: Verified safe cleanup - only remove if ownership token is strictly validated
    if (Test-Path $tokenFile) {
        $readToken = (Get-Content $tokenFile -Raw).Trim()
        if ($readToken -eq $ownershipToken) {
            if ($drillSuccess) {
                Write-Host "Step 7: Removing verified temporary drill directory ($drillDir)..."
                Remove-Item $drillDir -Recurse -Force
            } else {
                Write-Warning "Drill failed; preserving evidence directory for diagnostic inspection: $drillDir"
            }
        } else {
            Write-Warning "Ownership token mismatch; preserving directory for security: $drillDir"
        }
    } else {
        Write-Warning "Ownership token file missing; preserving directory for security: $drillDir"
    }
}

Write-Host "OFFLINE_BUNDLE_RESTORATION_DRILL_PASSED_CLEANLY"
