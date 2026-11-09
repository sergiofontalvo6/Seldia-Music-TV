#!/usr/bin/env bash
# Seldia Music TV - Instalación por ADB en TVs locales (Linux/macOS)
# TV1: 192.168.100.129  | TV2: 192.168.100.177
# Uso:
#   ./install_tv.sh          -> ambas TVs
#   ./install_tv.sh tv1      -> solo TV1
#   ./install_tv.sh tv2      -> solo TV2
set -e
cd "$(dirname "$0")"

TV1="192.168.100.129"
TV2="192.168.100.177"
APK="app/build/outputs/apk/debug/app-debug.apk"
ACT="com.seldia.music/.ui.MainActivity"
ADB="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/platform-tools/adb"
[ -x "$ADB" ] || ADB="adb"

install_one() {
  local ip="$1" name="$2"
  echo "[$name] $ip : adb connect..."
  "$ADB" connect "$ip"
  sleep 2
  "$ADB" -s "$ip:5555" install -r "$APK" || echo "AVISO: instalación en $name falló."
  "$ADB" -s "$ip:5555" shell am start -n "$ACT" || true
}

echo "[Seldia Music TV] Compilando APK debug..."
./gradlew :app:assembleDebug

case "$1" in
  tv1) install_one "$TV1" "TV1" ;;
  tv2) install_one "$TV2" "TV2" ;;
  disconnect) "$ADB" disconnect "$TV1"; "$ADB" disconnect "$TV2" ;;
  *) install_one "$TV1" "TV1"; install_one "$TV2" "TV2" ;;
esac

echo "Listo. Busca 'Seldia Music TV' en el menú principal (Leanback)."
