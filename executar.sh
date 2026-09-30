#!/usr/bin/env bash
# BCraftOS - abre o launcher no Linux e no Mac.
# Se der erro de permissao, rode uma vez:  chmod +x executar.sh

cd "$(cd "$(dirname "$0")" && pwd)" || exit 1

echo "================================================="
echo "           BCraftOS - Abrindo o launcher"
echo "================================================="
echo

if ! command -v javac >/dev/null 2>&1; then
  echo "NAO ACHEI O COMPILADOR JAVA (javac) neste computador."
  echo
  echo "Instale o JDK 17 ou mais novo:"
  echo "  Ubuntu/Debian : sudo apt install openjdk-17-jdk"
  echo "  Fedora        : sudo dnf install java-17-openjdk-devel"
  echo "  Arch          : sudo pacman -S jdk17-openjdk"
  echo "  Mac           : brew install openjdk@17"
  exit 1
fi

echo "[1/3] Compilando os arquivos..."
mkdir -p saida
ARQUIVOS=$(find src -name '*.java' 2>/dev/null)
if [ -z "$ARQUIVOS" ]; then
  echo
  echo "NAO ACHEI os arquivos .java na pasta src."
  echo "Rode primeiro o instalador:  java InstaladorBCraftOS"
  exit 1
fi

javac -encoding UTF-8 -d saida $ARQUIVOS

if [ $? -ne 0 ]; then
  echo
  echo "DEU ERRO AO COMPILAR. As mensagens estao logo acima."
  echo "Motivo mais comum: arquivo repetido do tipo BCraftOS1(1).java. Apague e tente de novo."
  exit 1
fi
echo "      Compilado sem erros."
echo

echo "[2/3] Preparando as pastas..."
mkdir -p versoes usuarios
echo "      Pastas prontas."
echo

echo "[3/3] Abrindo o BCraftOS..."
echo
java -cp saida BCraftOSproject1.BCraftOS1login.BCraftOS1login

echo
echo "Launcher fechado. Ate a proxima!"

