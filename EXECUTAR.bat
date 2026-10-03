@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1
title BCraftOS Launcher
cd /d "%~dp0"

echo =================================================
echo            BCraftOS - Abrindo o launcher
echo =================================================
echo.

rem Garante o Java: usa o do sistema (17+) ou baixa sozinho para a pasta java\.
if exist "%~dp0instalar-jdk.bat" (
    call "%~dp0instalar-jdk.bat"
    if errorlevel 1 goto SEM_JAVA
) else (
    where javac >nul 2>&1
    if errorlevel 1 goto SEM_JAVA
)
where java >nul 2>&1
if errorlevel 1 goto SEM_JAVA

echo [1/3] Compilando os arquivos...
if not exist "saida" mkdir "saida"
set FONTES=
for /r "src" %%f in (*.java) do set FONTES=!FONTES! "%%f"
if "!FONTES!"=="" (
    rem src ainda nao existe: compila os .java soltos desta pasta (ja trazem o package certo).
    for %%f in (*.java) do (
        if /i not "%%f"=="InstaladorBCraftOS.java" set FONTES=!FONTES! "%%f"
    )
)
if "!FONTES!"=="" goto SEM_FONTES

if not exist "logs" mkdir "logs"
javac -encoding UTF-8 -d "saida" !FONTES! > "logs\compilacao.log" 2>&1
if errorlevel 1 (
    type "logs\compilacao.log"
    goto ERRO_COMPILAR
)
echo       Compilado sem erros.
echo.

echo [2/3] Preparando as pastas...
if not exist "versoes" mkdir "versoes"
if not exist "usuarios" mkdir "usuarios"
echo       Pastas prontas.
echo.

echo [3/3] Abrindo o BCraftOS...
echo.
rem O proprio launcher e leve: teto de memoria baixo e coletor simples, para sobrar RAM ao jogo (PC de 2 GB).
java -Xmx512m -XX:+UseSerialGC -Dfile.encoding=UTF-8 -cp "saida" BCraftOSproject1.BCraftOS1login.BCraftOS1login
if errorlevel 1 goto ERRO_ABRIR

echo.
echo Launcher fechado. Ate a proxima!
pause
exit /b 0

:SEM_JAVA
echo.
echo NAO CONSEGUI DEIXAR O JAVA PRONTO neste computador.
echo.
echo O launcher precisa do Java Development Kit (JDK) 17 ou mais novo.
echo Baixe de graca aqui: https://adoptium.net/temurin/releases/
echo Na instalacao, marque a opcao "Add to PATH".
echo Depois de instalar, feche esta janela e abra de novo.
pause
exit /b 1

:SEM_FONTES
echo.
echo NAO ACHEI os arquivos .java do launcher.
echo.
echo Rode primeiro o instalador: inicializador_do_instalador.bat
pause
exit /b 1

:ERRO_COMPILAR
echo.
echo DEU ERRO AO COMPILAR. As mensagens estao acima e salvas em logs\compilacao.log
echo.
echo O motivo mais comum e um arquivo repetido do tipo BCraftOS1(1).java.
echo Nesse caso apague o arquivo com o (1) no nome e rode de novo.
pause
exit /b 1

:ERRO_ABRIR
echo.
echo O launcher fechou com erro. As mensagens estao logo acima.
pause
exit /b 1
