$ErrorActionPreference = "Stop"
$bundle = "C:\Users\astra\Desktop\Project\HireFlow-Recovery\git_bundles\hireflow_cap864_all_refs.bundle"
Write-Host "Verifying Git bundle at $bundle..."
& git bundle verify $bundle
if ($LASTEXITCODE -ne 0) { throw "Bundle verification failed" }

$drillDir = "C:\Users\astra\Desktop\Project\HireFlow-Recovery\drill_test_restore"
if (Test-Path $drillDir) {
    Remove-Item $drillDir -Recurse -Force
}

Write-Host "Cloning from offline bundle into test directory..."
& git clone $bundle $drillDir
Push-Location $drillDir
try {
    & git checkout develop/stage5
    $log = (& git log -1 --oneline)
    Write-Host "Restored commit HEAD: $log"
    $tags = (& git tag -l)
    Write-Host "Restored tags: $($tags -join ', ')"
} finally {
    Pop-Location
    Remove-Item $drillDir -Recurse -Force
}
Write-Host "OFFLINE_BUNDLE_RESTORATION_DRILL_PASSED_CLEANLY"
