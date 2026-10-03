#!/usr/bin/env bash
# Compila e abre o instalador do BCraftOS no Linux/Mac.
# Se der erro de permissao, rode uma vez:  chmod +x inicializador_do_instalador.sh
cd "$(dirname "$0")" || exit 1

if [ -f ./instalar-jdk.sh ]; then
    # shellcheck disable=SC1091
    source ./instalar-jdk.sh
    if ! garantir_jdk; then
        read -r -p "Aperte Enter para fechar..." _
        exit 1
    fi
elif ! command -v javac >/dev/null 2>&1; then
    echo "Nao achei o javac. Instale o JDK 17 ou mais novo."
    echo "No Linux Mint/Ubuntu:  sudo apt install openjdk-21-jdk"
    read -r -p "Aperte Enter para fechar..." _
    exit 1
fi

if ! javac -encoding UTF-8 InstaladorBCraftOS.java; then
    echo
    echo "Nao consegui compilar o InstaladorBCraftOS.java."
    read -r -p "Aperte Enter para fechar..." _
    exit 1
fi

java InstaladorBCraftOS
read -r -p "Aperte Enter para fechar..." _
