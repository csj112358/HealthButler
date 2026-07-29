@echo off
title HealthButler - Starting

echo ========================================
echo    Health Butler - One Click Start
echo    Path: %~dp0
echo ========================================
echo.

:: Start backend in new window
echo [1/2] Starting backend (Spring Boot)...
start "HealthButler-Backend" /D "%~dp0" cmd /k mvn spring-boot:run

:: Wait for backend to start
echo [2/2] Waiting 25s for backend...
ping 127.0.0.1 -n 26 >nul

:: Start frontend in new window
echo     Starting frontend (Vite)...
start "HealthButler-Frontend" /D "%~dp0frontend" cmd /k npm run dev

:: Wait for frontend to start
echo     Waiting 8s for frontend...
ping 127.0.0.1 -n 9 >nul

:: Open browser
echo.
echo Opening browser...
start http://localhost:3000

echo.
echo ========================================
echo    Done!
echo    Backend:  http://localhost:8080
echo    Frontend: http://localhost:3000
echo ========================================
echo.
echo Close this window does not affect services.
echo Run stop.bat to stop all services.
echo.
pause
