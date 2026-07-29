@echo off
title HealthButler - Stop

echo ========================================
echo    Health Butler - Stop Services
echo ========================================
echo.

echo Stopping backend...
taskkill /FI "WINDOWTITLE eq HealthButler-Backend*" /F >nul 2>&1

echo Stopping frontend...
taskkill /FI "WINDOWTITLE eq HealthButler-Frontend*" /F >nul 2>&1

echo.
echo All services stopped.
echo.
pause
