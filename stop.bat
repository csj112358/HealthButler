@echo off
title C_Check_Up_Home - Stop
cd /d "%~dp0"
setlocal enabledelayedexpansion

echo ========================================
echo    Health Butler - Stop All Services
echo ========================================
echo/

:: ============================================
:: 1. Kill by window title
:: ============================================
echo [1/3] Stopping service windows...

set KILLED=0

taskkill /FI "WINDOWTITLE eq C_Check_Up_Home-Backend*" /F >nul 2>&1
if !errorlevel!==0 (
    echo         Backend window closed
    set KILLED=1
)
taskkill /FI "WINDOWTITLE eq run_backend*" /F >nul 2>&1
if !errorlevel!==0 (
    echo         Backend runner closed
    set KILLED=1
)

taskkill /FI "WINDOWTITLE eq C_Check_Up_Home-Frontend*" /F >nul 2>&1
if !errorlevel!==0 (
    echo         Frontend window closed
    set KILLED=1
)
taskkill /FI "WINDOWTITLE eq run_frontend*" /F >nul 2>&1
if !errorlevel!==0 (
    echo         Frontend runner closed
    set KILLED=1
)

:: ============================================
:: 2. Kill by port (fallback)
:: ============================================
echo/
echo [2/3] Cleaning up by port...

for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":8080.*LISTENING"') do (
    echo         Killing port 8080 (PID: %%a)
    taskkill /PID %%a /F >nul 2>&1
    set KILLED=1
)

for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":3000.*LISTENING"') do (
    echo         Killing port 3000 (PID: %%a)
    taskkill /PID %%a /F >nul 2>&1
    set KILLED=1
)

:: ============================================
:: 3. Verify
:: ============================================
echo/
echo [3/3] Verifying ports...

netstat -ano 2>nul | findstr ":8080.*LISTENING" >nul 2>&1
if !errorlevel!==0 (
    echo         [WARN] Port 8080 may still be in use
) else (
    echo         Port 8080 released
)

netstat -ano 2>nul | findstr ":3000.*LISTENING" >nul 2>&1
if !errorlevel!==0 (
    echo         [WARN] Port 3000 may still be in use
) else (
    echo         Port 3000 released
)

:: ============================================
:: Done
:: ============================================
echo/
if !KILLED!==0 (
    echo No running services found.
) else (
    echo All services stopped.
)
echo/
pause
