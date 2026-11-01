#!/bin/sh
# Render one mockup to a 1920x1080 PNG.   usage: render.sh <html name without ext> [out name]
# The dedicated user-data-dir keeps headless Edge away from any running browser.
SRC=/d/dev/tierprofiles/design/src; OUT=${3:-/d/dev/tierprofiles/design/shots}
"/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" --headless --disable-gpu \
  --user-data-dir="D:/dev/tierprofiles/design/_tmp/edge" --no-first-run \
  --force-device-scale-factor=1 --hide-scrollbars --disable-lcd-text --window-size=1920,1080 \
  --run-all-compositor-stages-before-draw --virtual-time-budget=4000 \
  --allow-file-access-from-files \
  --screenshot="D:/dev/tierprofiles/design/${4:-shots}/${2:-$1}.png" \
  "file:///D:/dev/tierprofiles/design/src/$1.html" >/dev/null 2>&1
