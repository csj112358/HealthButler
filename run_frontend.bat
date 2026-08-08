@echo off
title C_Check_Up_Home-Frontend
cd /d "%~dp0frontend"

echo ========================================
echo    Health Butler - Frontend
echo    Path: %cd%
echo ========================================

echo Starting Vite dev server...
call npm run dev

echo ========================================
echo Frontend stopped.
echo ========================================
pause
