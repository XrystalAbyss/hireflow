@echo off
setlocal enabledelayedexpansion

echo ==============================================================================
echo HireFlow - Unified Multi-Artifact Reproducible Build Script
echo ==============================================================================
echo Java Version:
java -version
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java is not installed or not in PATH. Java 21 LTS is required.
    exit /b 1
)

echo.
echo ------------------------------------------------------------------------------
echo Step 1: Building and installing HireFlow core, WAR, and desktop JAR
echo ------------------------------------------------------------------------------
call mvn clean install %*
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Root HireFlow build failed with exit code %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo.
echo ------------------------------------------------------------------------------
echo Step 2: Building and packaging HireFlow Spring Boot REST API
echo ------------------------------------------------------------------------------
call mvn -f hireflow-api/pom.xml clean package %*
if %ERRORLEVEL% neq 0 (
    echo [ERROR] HireFlow REST API build failed with exit code %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo.
echo ==============================================================================
echo [SUCCESS] All HireFlow artifacts compiled, tested, and packaged successfully!
echo Artifact 1: target\hireflow-1.0.0.war          (Servlet 6.0 Web Portal)
echo Artifact 2: target\hireflow-1.0.0-desktop.jar  (Stage 2 Swing Desktop App)
echo Artifact 3: target\hireflow-1.0.0-classes.jar  (Core Domain/Service Classes)
echo Artifact 4: hireflow-api\target\hireflow-api-1.0.0.jar (Spring Boot Executable)
echo ==============================================================================
exit /b 0
