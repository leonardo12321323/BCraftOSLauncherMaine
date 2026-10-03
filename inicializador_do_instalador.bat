@echo off
chcp 65001 >nul 2>&1
cd /d "%~dp0"

if exist "%~dp0instalar-jdk.bat" (
    call "%~dp0instalar-jdk.bat"
    if errorlevel 1 (
        pause
        exit /b 1
    )
)

javac -encoding UTF-8 InstaladorBCraftOS.java
if errorlevel 1 (
    echo.
    echo Nao consegui compilar. Confira se o JDK 17 ou mais novo esta instalado ^(precisa do javac^).
    pause
    exit /b 1
)

java InstaladorBCraftOS
pause
