@echo off
setlocal

cd /d "%~dp0"

set "POWERSHELL_EXE=pwsh.exe"
where pwsh >nul 2>&1
if errorlevel 1 (
    set "POWERSHELL_EXE=powershell.exe"
)

"%POWERSHELL_EXE%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy.ps1" %*
exit /b %errorlevel%
