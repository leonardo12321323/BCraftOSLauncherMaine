#!/usr/bin/env bash
# BCraftOS - abre o launcher no Linux e no Mac.
# Se der erro de permissao, rode uma vez:  chmod +x executar.sh

cd "$(cd "$(dirname "$0")" && pwd)" || exit 1

echo "================================================="
echo "           BCraftOS - Abrindo o launcher"
echo "================================================="
echo

pausar() { read -r -p "Aperte Enter para fechar..." _; }

# Garante o Java: usa o do sistema (17+) ou baixa sozinho para a pasta java/.
if [ -f ./instalar-jdk.sh ]; then
  # shellcheck disable=SC1091
  source ./instalar-jdk.sh
  if ! garantir_jdk; then
    echo
    echo "Nao consegui deixar o Java pronto. Instale o JDK 17 ou mais novo:"
    echo "  Ubuntu/Debian : sudo apt install openjdk-21-jdk"
    echo "  Fedora        : sudo dnf install java-21-openjdk-devel"
    echo "  Arch          : sudo pacman -S jdk21-openjdk"
    echo "  Mac           : brew install openjdk@21"
    pausar; exit 1
  fi
elif ! command -v javac >/dev/null 2>&1; then
  echo "NAO ACHEI O COMPILADOR JAVA (javac) e o arquivo instalar-jdk.sh nao esta nesta pasta."
  echo "Instale o JDK 17 ou mais novo (sudo apt install openjdk-21-jdk) e tente de novo."
  pausar; exit 1
fi

echo "[1/3] Compilando os arquivos..."
mkdir -p saida
# Usa a pasta src/ (organizada). Se ela ainda nao existe, compila os .java soltos desta pasta:
# eles ja trazem o "package" certo, entao funciona do mesmo jeito e o launcher se organiza sozinho.
ARQUIVOS=$(find src -name '*.java' 2>/dev/null)
if [ -z "$ARQUIVOS" ]; then
  ARQUIVOS=$(find . -maxdepth 1 -name '*.java' ! -name 'InstaladorBCraftOS.java' 2>/dev/null)
fi
if [ -z "$ARQUIVOS" ]; then
  echo
  echo "NAO ACHEI os arquivos .java do launcher."
  echo "Rode primeiro o instalador (pasta BCraftOSInstalador): ./inicializador_do_instalador.sh"
  pausar; exit 1
fi

mkdir -p logs
# shellcheck disable=SC2086
javac -encoding UTF-8 -d saida $ARQUIVOS > logs/compilacao.log 2>&1
if [ $? -ne 0 ]; then
  cat logs/compilacao.log
  echo
  echo "DEU ERRO AO COMPILAR. As mensagens estao acima e salvas em logs/compilacao.log"
  echo "Motivo mais comum: arquivo repetido do tipo BCraftOS1(1).java. Apague e tente de novo."
  pausar; exit 1
fi
echo "      Compilado sem erros."
echo

echo "[2/3] Preparando as pastas..."
mkdir -p versoes usuarios
echo "      Pastas prontas."
echo

echo "[3/3] Abrindo o BCraftOS..."
echo
# O proprio launcher e leve: teto de memoria baixo e coletor simples, para sobrar RAM ao jogo (PC de 2 GB).
java -Xmx512m -XX:+UseSerialGC -Dfile.encoding=UTF-8 -cp saida BCraftOSproject1.BCraftOS1login.BCraftOS1login
CODIGO=$?

echo
if [ $CODIGO -ne 0 ]; then
  echo "O launcher fechou com erro (codigo $CODIGO). As mensagens estao logo acima."
else
  echo "Launcher fechado. Ate a proxima!"
fi
pausar
exit $CODIGO
