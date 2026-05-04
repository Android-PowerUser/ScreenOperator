#!/usr/bin/env bash
set -euo pipefail

# Virtueller Desktop (GUI)
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y xvfb x11vnc fluxbox xterm scrot curl jq ca-certificates lxc uidmap dbus

mkdir -p /tmp/virt-desktop
Xvfb :2 -screen 0 1920x1080x24 >/tmp/virt-desktop/xvfb2.log 2>&1 &
for _ in $(seq 1 20); do
  DISPLAY=:2 xdpyinfo >/dev/null 2>&1 && break
  sleep 1
done
fluxbox -display :2 >/tmp/virt-desktop/fluxbox2.log 2>&1 &
x11vnc -display :2 -nopw -forever -shared -rfbport 5902 >/tmp/virt-desktop/x11vnc2.log 2>&1 &
DISPLAY=:2 xterm -display :2 -e 'echo Virtueller Desktop aktiv; bash' >/tmp/virt-desktop/xterm2.log 2>&1 &

# Alternative zum Android SDK Emulator: Waydroid (LXC-basiert)
# Repository + Installation
curl -fsSL https://repo.waydro.id | bash
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y waydroid

# Waydroid initialisieren und starten
waydroid init
systemctl start waydroid-container || true
waydroid session start

# Screenshots (Desktop + Android-Container)
DISPLAY=:2 scrot /tmp/virt-desktop/desktop2.png
waydroid screenshot /tmp/virt-desktop/waydroid_screen.png

# Aktuelle Termux-APK-URL bereitstellen
curl -fsSL https://api.github.com/repos/termux/termux-app/releases/latest | jq -r '.assets[] | select(.name|test("github-debug_universal\\.apk$")) | .browser_download_url' | head -n1 > /tmp/virt-desktop/termux_latest_url.txt
