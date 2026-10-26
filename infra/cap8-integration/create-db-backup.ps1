param(
    [string]$Prefix = 'hireflow_cap8_it_pre_cap8631'
)
$ErrorActionPreference = 'Stop'
$ts = Get-Date -Format 'yyyyMMdd_HHmmss'
$backupDir = 'C:\Users\astra\Desktop\Project\HireFlow-Recovery\database_backups'
if (!(Test-Path $backupDir)) {
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
}
$dumpName = "${Prefix}_${ts}.dump"
$dumpPath = Join-Path $backupDir $dumpName
$tempContainerFile = "/tmp/${dumpName}"

Write-Host "Dumping PostgreSQL database hireflow_cap8_it from container hireflow-cap8-postgres..."
& docker exec hireflow-cap8-postgres pg_dump -U postgres -d hireflow_cap8_it -Fc -f $tempContainerFile
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed" }

& docker cp "hireflow-cap8-postgres:${tempContainerFile}" $dumpPath
if ($LASTEXITCODE -ne 0) { throw "docker cp failed" }

& docker exec hireflow-cap8-postgres rm $tempContainerFile

$fileInfo = Get-Item $dumpPath
$hash = (Get-FileHash -Path $dumpPath -Algorithm SHA256).Hash

Write-Host "DUMP_PATH: $dumpPath"
Write-Host "DUMP_SIZE: $($fileInfo.Length) bytes"
Write-Host "DUMP_SHA256: $hash"

# Verify dump catalog is readable
& docker cp $dumpPath "hireflow-cap8-postgres:/tmp/verify_${dumpName}"
$catalog = & docker exec hireflow-cap8-postgres pg_restore -l "/tmp/verify_${dumpName}"
& docker exec hireflow-cap8-postgres rm "/tmp/verify_${dumpName}"
$catalogLines = ($catalog -split "`n").Count
Write-Host "CATALOG_ENTRIES: $catalogLines"
