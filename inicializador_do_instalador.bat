@echo off
chcp 65001 >nul
cd /d "%~dp0"

javac -encoding UTF-8 InstaladorBCraftOS.java
if errorlevel 1 (
    echo.
    echo Nao consegui compilar. Confira se o JDK 17 ou mais novo esta instalado ^(precisa do javac^).
    pause
    exit /b 1
)

java InstaladorBCraftOS
pause
