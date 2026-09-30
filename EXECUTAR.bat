@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1
title BCraftOS Launcher
cd /d "%~dp0"

echo =================================================
echo            BCraftOS - Abrindo o launcher
echo =================================================
echo.

where javac >nul 2>&1
if errorlevel 1 goto SEM_JAVA
where java >nul 2>&1
if errorlevel 1 goto SEM_JAVA

echo [1/3] Compilando os arquivos...
if not exist "saida" mkdir "saida"
set FONTES=
for /r "src" %%f in (*.java) do set FONTES=!FONTES! "%%f"
if "!FONTES!"=="" goto SEM_FONTES

javac -encoding UTF-8 -d "saida" !FONTES!
if errorlevel 1 goto ERRO_COMPILAR
echo       Compilado sem erros.
echo.

echo [2/3] Preparando as pastas...
if not exist "versoes" mkdir "versoes"
if not exist "usuarios" mkdir "usuarios"
echo       Pastas prontas.
echo.

echo [3/3] Abrindo o BCraftOS...
echo.
java -cp "saida" BCraftOSproject1.BCraftOS1login.BCraftOS1login
if errorlevel 1 goto ERRO_ABRIR

echo.
echo Launcher fechado. Ate a proxima!
pause
exit /b 0

:SEM_JAVA
echo.
echo NAO ACHEI O JAVA neste computador.
echo.
echo O launcher precisa do Java Development Kit (JDK) 17 ou mais novo.
echo Baixe de graca aqui: https://adoptium.net/temurin/releases/
echo Na instalacao, marque a opcao "Add to PATH".
echo Depois de instalar, feche esta janela e abra de novo.
pause
exit /b 1

:SEM_FONTES
echo.
echo NAO ACHEI os arquivos .java na pasta src.
echo.
echo Se eles nao estiverem ai, rode primeiro o instalador:
echo    java InstaladorBCraftOS
pause
exit /b 1

:ERRO_COMPILAR
echo.
echo DEU ERRO AO COMPILAR. As mensagens estao logo acima.
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
