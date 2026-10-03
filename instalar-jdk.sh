#!/usr/bin/env bash
# BCraftOS - instalador automatico de JDK (Linux e Mac).
#
# Uso (dentro de outro script):   source "$(dirname "$0")/instalar-jdk.sh" && garantir_jdk || exit 1
#
# Se o computador ja tem um JDK 17 ou mais novo, usa ele. Se nao tem, baixa o Temurin 21
# (Eclipse Adoptium, gratuito) para a pasta ./java/jdk-21 DESTA pasta. Nao precisa de sudo,
# nao instala nada no sistema e nao mexe em outros Java que voce tenha.

BC_RAIZ_JDK="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BC_VERSAO_JDK=21

_bc_versao_javac() {
  javac -version 2>&1 | sed -E 's/^javac ([0-9]+).*/\1/;t;s/.*//'
}

_bc_usar_jdk() {
  export JAVA_HOME="$1"
  export PATH="$JAVA_HOME/bin:$PATH"
  echo "      Usando o JDK: $JAVA_HOME"
}

_bc_achar_casa() {
  # No Mac o JDK fica em Contents/Home; no resto, na propria pasta.
  if   [ -x "$1/Contents/Home/bin/javac" ]; then echo "$1/Contents/Home"
  elif [ -x "$1/bin/javac" ];               then echo "$1"
  fi
}

garantir_jdk() {
  # 1. JDK do sistema, se for 17+
  if command -v javac >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
    local v; v="$(_bc_versao_javac)"
    if [ -n "$v" ] && [ "$v" -ge 17 ] 2>/dev/null; then
      return 0
    fi
  fi

  # 2. JDK que o BCraftOS ja baixou antes
  local pasta="$BC_RAIZ_JDK/java/jdk-$BC_VERSAO_JDK" casa
  casa="$(_bc_achar_casa "$pasta")"
  if [ -n "$casa" ] && [ -f "$pasta/.bcraftos-java-ok" ]; then
    _bc_usar_jdk "$casa"
    return 0
  fi

  # 3. Baixa sozinho
  echo
  echo "Nao achei um JDK 17+ neste computador. Vou baixar o Java $BC_VERSAO_JDK sozinho"
  echo "(cerca de 200 MB, so na primeira vez, sem precisar de administrador)."
  echo

  local so arq
  case "$(uname -s)" in
    Linux)  so="linux"; [ -f /etc/alpine-release ] && so="alpine-linux" ;;
    Darwin) so="mac" ;;
    *) echo "Sistema nao suportado para o download automatico: $(uname -s)"; return 1 ;;
  esac
  case "$(uname -m)" in
    x86_64|amd64)  arq="x64" ;;
    aarch64|arm64) arq="aarch64" ;;
    *) echo "Arquitetura nao suportada para o download automatico: $(uname -m)"
       echo "Instale o JDK 17+ pelo gerenciador de pacotes do seu sistema."
       return 1 ;;
  esac

  local url="https://api.adoptium.net/v3/binary/latest/$BC_VERSAO_JDK/ga/$so/$arq/jdk/hotspot/normal/eclipse"
  local tmp="$BC_RAIZ_JDK/java/.baixando-$BC_VERSAO_JDK"
  rm -rf "$tmp"; mkdir -p "$tmp" || { echo "Nao consegui criar a pasta java/ aqui."; return 1; }

  echo "[JDK] Baixando de $url"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 4 --retry-delay 2 --connect-timeout 20 -o "$tmp/jdk.tar.gz" "$url" || { echo "Falhou o download."; rm -rf "$tmp"; return 1; }
  elif command -v wget >/dev/null 2>&1; then
    wget -t 4 -O "$tmp/jdk.tar.gz" "$url" || { echo "Falhou o download."; rm -rf "$tmp"; return 1; }
  else
    echo "Preciso do curl ou do wget para baixar. Instale um deles:  sudo apt install curl"
    rm -rf "$tmp"; return 1
  fi

  echo "[JDK] Conferindo o arquivo..."
  gzip -t "$tmp/jdk.tar.gz" 2>/dev/null || { echo "O arquivo veio corrompido. Rode de novo."; rm -rf "$tmp"; return 1; }

  echo "[JDK] Extraindo..."
  mkdir -p "$tmp/extraido"
  tar -xzf "$tmp/jdk.tar.gz" -C "$tmp/extraido" || { echo "Nao consegui extrair."; rm -rf "$tmp"; return 1; }

  local topo; topo="$(find "$tmp/extraido" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
  if [ -z "$topo" ] || [ -z "$(_bc_achar_casa "$topo")" ]; then
    echo "O pacote baixado nao tem o javac. Rode de novo."; rm -rf "$tmp"; return 1
  fi

  rm -rf "$pasta"
  mv "$topo" "$pasta" || { echo "Nao consegui mover o JDK para $pasta"; rm -rf "$tmp"; return 1; }
  echo ok > "$pasta/.bcraftos-java-ok"
  rm -rf "$tmp"

  casa="$(_bc_achar_casa "$pasta")"
  _bc_usar_jdk "$casa"
  echo "[JDK] Java $BC_VERSAO_JDK instalado em $pasta"
  echo
  return 0
}
