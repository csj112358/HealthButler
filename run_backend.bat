@echo off
title C_Check_Up_Home-Backend
cd /d "%~dp0"
setlocal enabledelayedexpansion

echo ========================================
echo    Health Butler - Backend
echo    Path: %cd%
echo ========================================

:: Ensure MySQL is running
echo Checking MySQL...
netstat -ano | findstr ":3306.*LISTENING" >nul 2>&1
if errorlevel 1 (
    echo MySQL not running, trying service...
    net start MySQL80 >nul 2>&1
    ping 127.0.0.1 -n 3 >nul
    netstat -ano | findstr ":3306.*LISTENING" >nul 2>&1
    if errorlevel 1 (
        echo Service failed, starting mysqld directly...
        start "MySQL" /MIN "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysqld.exe" --standalone
        :: Wait for port 3306
        set /a N=0
        :wait_mysql
        netstat -ano | findstr ":3306.*LISTENING" >nul 2>&1
        if errorlevel 1 (
            ping 127.0.0.1 -n 2 >nul
            set /a N+=1
            if !N! lss 20 goto wait_mysql
        )
    )
)
netstat -ano | findstr ":3306.*LISTENING" >nul 2>&1
if errorlevel 1 (
    echo [ERROR] MySQL failed to start!
    pause
    exit /b 1
)
echo MySQL ready.

echo Starting Spring Boot with JDK 17...
set JAVA_HOME=C:\Program Files\Java\jdk-17
set PATH=C:\Program Files\Java\jdk-17\bin;%PATH%
java -version
call mvn spring-boot:run

echo ========================================
echo Backend stopped.
echo ========================================
pause
