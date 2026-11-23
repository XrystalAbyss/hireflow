# ==============================================================================
# HireFlow — Build Portable Full-Stack Windows Presentation Package
# Generates HireFlow-FullStack-Presentation.zip containing:
# - Single-origin Spring Boot 4 + React 19 executable JAR
# - Bundled Eclipse Temurin Java 21 JRE runtime (via jlink)
# - Windows CMD launchers (Launch-HireFlow.cmd, Check-HireFlow.cmd, Stop-HireFlow.cmd)
# - Secret-free configuration template and quick-start README.txt
# ==============================================================================
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$targetDir = Join-Path $root 'target\HireFlow-FullStack-Presentation'
$zipPath = Join-Path $root 'HireFlow-FullStack-Presentation.zip'

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "  HIREFLOW - BUILDING PORTABLE PRESENTATION PACKAGE" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

# 1. Build React Frontend
Write-Host "`n[1/5] Building React 19 Production Bundle..." -ForegroundColor Yellow
Set-Location "$root\hireflow-app"
& npm run build
if ($LASTEXITCODE -ne 0) { throw "Frontend production build failed." }

# 2. Copy Frontend Dist to Spring Boot Static Resources
Write-Host "`n[2/5] Embedding Frontend Bundle into Spring Boot Static Resources..." -ForegroundColor Yellow
$staticDir = "$root\hireflow-api\src\main\resources\static"
if (Test-Path $staticDir) { Remove-Item -Recurse -Force $staticDir }
New-Item -ItemType Directory -Path $staticDir -Force | Out-Null
Copy-Item -Recurse -Force "$root\hireflow-app\dist\*" $staticDir

# 3. Package Executable JAR
Write-Host "`n[3/5] Packaging Full-Stack Spring Boot Executable JAR..." -ForegroundColor Yellow
Set-Location $root
& mvn -B -f hireflow-api/pom.xml clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) { throw "Backend JAR packaging failed." }

# 4. Generate Lightweight JRE via JLink (if jre not already present)
$jreTarget = "$targetDir\jre"
if (-not (Test-Path "$jreTarget\bin\java.exe")) {
    Write-Host "`n[4/5] Creating Optimized Java 21 JRE Runtime via jlink..." -ForegroundColor Yellow
    $jlink = "C:\Users\astra\.jdks\temurin-21\bin\jlink.exe"
    if (-not (Test-Path $jlink)) {
        $jlinkCmd = Get-Command jlink.exe -ErrorAction SilentlyContinue
        if ($jlinkCmd) { $jlink = $jlinkCmd.Source }
        else { throw "jlink.exe not found. Ensure Java 21 JDK is installed." }
    }
    if (Test-Path $jreTarget) { Remove-Item -Recurse -Force $jreTarget }
    & $jlink --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.management --strip-debug --no-man-pages --no-header-files --output $jreTarget
}

# 5. Copy JAR into presentation app directory
New-Item -ItemType Directory -Path "$targetDir\app" -Force | Out-Null
Copy-Item -Force "$root\hireflow-api\target\hireflow-api-1.0.0.jar" "$targetDir\app\hireflow-api.jar"

# 6. Compress ZIP Distribution
Write-Host "`n[5/5] Compressing HireFlow-FullStack-Presentation.zip..." -ForegroundColor Yellow
if (Test-Path $zipPath) { Remove-Item -Force $zipPath }
Compress-Archive -Path "$targetDir\*" -DestinationPath $zipPath -CompressionLevel Optimal

$zipItem = Get-Item $zipPath
$sizeMb = [math]::Round($zipItem.Length / 1MB, 1)
Write-Host "`n==============================================================================" -ForegroundColor Green
Write-Host "  PACKAGE CREATED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "  Location: $zipPath" -ForegroundColor Green
Write-Host "  Archive Size: $sizeMb MB" -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Green
