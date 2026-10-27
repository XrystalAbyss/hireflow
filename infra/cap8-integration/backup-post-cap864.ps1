$ErrorActionPreference = "Stop"
$ts = Get-Date -Format 'yyyyMMdd_HHmmss'
$dir = "C:\Users\astra\Desktop\Project\HireFlow-Recovery\database_backups"
if (-not (Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
}
$dest = Join-Path $dir "hireflow_cap8_it_post_cap864_${ts}.dump"
Write-Host "Creating logical backup in container..."
& docker exec hireflow-cap8-postgres pg_dump -U hireflow_cap8_app -d hireflow_cap8_it -Fc -f /tmp/backup.dump
Write-Host "Copying backup to ${dest}..."
& docker cp hireflow-cap8-postgres:/tmp/backup.dump $dest
$item = Get-Item $dest
$hash = (Get-FileHash -Path $dest -Algorithm SHA256).Hash
Write-Host "BACKUP_PATH: $($item.FullName)"
Write-Host "BACKUP_SIZE: $($item.Length) bytes"
Write-Host "BACKUP_SHA256: $hash"

# Verify readability with pg_restore list catalog
Write-Host "Verifying backup catalog..."
$catalog = & docker exec hireflow-cap8-postgres pg_restore -l /tmp/backup.dump
Write-Host "CATALOG_ENTRIES: $($catalog.Count)"
