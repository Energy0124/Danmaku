@echo off
setlocal

cd /d "%~dp0"

where cargo >nul 2>&1
if errorlevel 1 (
    echo ERROR: cargo was not found on PATH.
    exit /b 1
)

where npm >nul 2>&1
if errorlevel 1 (
    echo ERROR: npm was not found on PATH.
    exit /b 1
)

set "POWERSHELL_EXE=pwsh.exe"
where pwsh >nul 2>&1
if errorlevel 1 (
    set "POWERSHELL_EXE=powershell.exe"
)

if not exist "apps\web-ui\node_modules" (
    echo Installing web UI dependencies...
    pushd "apps\web-ui"
    call npm install
    if errorlevel 1 (
        popd
        echo ERROR: Web UI dependency installation failed.
        exit /b 1
    )
    popd
)

echo Building web UI...
pushd "apps\web-ui"
call npm run build
if errorlevel 1 (
    popd
    echo ERROR: Web UI build failed.
    exit /b 1
)
popd

echo Building and packaging the Rust player...
"%POWERSHELL_EXE%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\windows\prepare-rust-player-release.ps1"
if errorlevel 1 (
    echo ERROR: Rust player release build failed.
    exit /b 1
)

echo.
echo Rust player release is ready under:
echo   %~dp0build\release\rust-player
exit /b 0
