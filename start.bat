@echo off
start "C_Check_Up_Home-Backend" /D "%~dp0" cmd /k run_backend.bat
start "C_Check_Up_Home-Frontend" /D "%~dp0" cmd /k run_frontend.bat
