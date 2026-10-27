@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo\Stop-HireFlow.ps1" %*
set "HF_RESULT=%ERRORLEVEL%"
if not "%HIREFLOW_NO_PAUSE%"=="1" pause
exit /b %HF_RESULT%
