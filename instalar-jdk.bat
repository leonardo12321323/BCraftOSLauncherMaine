@echo off
rem BCraftOS - instalador automatico de JDK (Windows).
rem
rem Uso dentro de outro .bat:   call "%~dp0instalar-jdk.bat"
rem                             if errorlevel 1 goto SEM_JAVA
rem
rem Se o PC ja tem um JDK 17 ou mais novo, usa ele. Se nao tem, baixa o Temurin 21
rem (Eclipse Adoptium, gratuito) para a pasta java\jdk-21 DESTA pasta. Nao precisa
rem de administrador e nao instala nada no sistema.

set "BC_RAIZ=%~dp0"
set "BC_JDK=%BC_RAIZ%java\jdk-21"

rem 1. JDK do sistema, se for 17+
where javac >nul 2>&1
if errorlevel 1 goto LOCAL
where java >nul 2>&1
if errorlevel 1 goto LOCAL
for /f "tokens=2" %%v in ('javac -version 2^>^&1') do set "BC_VER=%%v"
for /f "tokens=1 delims=." %%m in ("%BC_VER%") do set "BC_MAIOR=%%m"
if "%BC_MAIOR%"=="" goto LOCAL
if %BC_MAIOR% GEQ 17 exit /b 0

:LOCAL
rem 2. JDK que o BCraftOS ja baixou antes
if exist "%BC_JDK%\.bcraftos-java-ok" if exist "%BC_JDK%\bin\javac.exe" goto USAR

rem 3. Baixa sozinho
echo.
echo Nao achei um JDK 17+ neste computador. Vou baixar o Java 21 sozinho
echo (cerca de 200 MB, so na primeira vez, sem precisar de administrador).
echo.

set "BC_ARQ=%PROCESSOR_ARCHITECTURE%"
if defined PROCESSOR_ARCHITEW6432 set "BC_ARQ=%PROCESSOR_ARCHITEW6432%"
set "BC_ARQ_API="
if /i "%BC_ARQ%"=="AMD64" set "BC_ARQ_API=x64"
if /i "%BC_ARQ%"=="ARM64" set "BC_ARQ_API=aarch64"
if "%BC_ARQ_API%"=="" (
    echo Este Windows e de 32 bits ou de uma arquitetura sem Java 21 para baixar.
    echo Instale um JDK 17+ manualmente: https://adoptium.net/temurin/releases/
    exit /b 1
)

set "BC_URL=https://api.adoptium.net/v3/binary/latest/21/ga/windows/%BC_ARQ_API%/jdk/hotspot/normal/eclipse"
set "BC_TMP=%BC_RAIZ%java\.baixando-21"
if exist "%BC_TMP%" rmdir /s /q "%BC_TMP%"
mkdir "%BC_TMP%" 2>nul
if errorlevel 1 (
    echo Nao consegui criar a pasta java\ aqui. Mova o launcher para uma pasta onde voce possa gravar.
    exit /b 1
)

echo [JDK] Baixando o Java 21...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri '%BC_URL%' -OutFile '%BC_TMP%\jdk.zip'"
if errorlevel 1 goto FALHOU
if not exist "%BC_TMP%\jdk.zip" goto FALHOU

echo [JDK] Extraindo...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Expand-Archive -LiteralPath '%BC_TMP%\jdk.zip' -DestinationPath '%BC_TMP%\extraido' -Force"
if errorlevel 1 goto FALHOU

if exist "%BC_JDK%" rmdir /s /q "%BC_JDK%"
for /d %%d in ("%BC_TMP%\extraido\*") do move "%%d" "%BC_JDK%" >nul
if not exist "%BC_JDK%\bin\javac.exe" goto FALHOU

echo ok> "%BC_JDK%\.bcraftos-java-ok"
rmdir /s /q "%BC_TMP%"
echo [JDK] Java 21 instalado em %BC_JDK%
echo.

:USAR
set "JAVA_HOME=%BC_JDK%"
set "PATH=%BC_JDK%\bin;%PATH%"
echo       Usando o JDK: %JAVA_HOME%
exit /b 0

:FALHOU
echo.
echo Nao consegui baixar ou extrair o Java. Confira a internet e tente de novo,
echo ou instale um JDK 17+ manualmente: https://adoptium.net/temurin/releases/
if exist "%BC_TMP%" rmdir /s /q "%BC_TMP%"
exit /b 1
