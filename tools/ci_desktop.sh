#!/bin/sh
# CI on a desktop: the example built with the ui tool for this desktop's native host, which
# compiles and links the package's native/macos, native/linux or native/windows code.
# tools/ci_desktop.sh linux|windows|macos
set -e
host="$1"
example=scan
case "$host" in
    linux|windows|macos) ;;
    *) echo "usage: tools/ci_desktop.sh linux|windows|macos"; exit 2 ;;
esac
root=$(cd "$(dirname "$0")/.." && pwd)
mkdir -p "$root/build"
cd "$root/examples/$example"
eskiuc fetch
ui_src="${ESKIU_HOME:-$HOME/.eskiu}/pkgs/ui/$(awk -F'\t' '$1 == "ui" { print $5 }' eskiu.lock)"
tool="$root/build/ui"
[ "$host" = windows ] && tool="$tool.exe"
eskiuc "$ui_src/tools/ui/main.esk" -o "$tool"
"$tool" build "$host"
echo "PASS desktop $host"
