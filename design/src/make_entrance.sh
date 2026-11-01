#!/bin/sh
# Render the card entrance at 30 fps: glide in, rows stagger, hold, glide out.
OUT=D:/dev/tierprofiles/design/_tmp/entrance
mkdir -p /d/dev/tierprofiles/design/_tmp/entrance
i=0
while [ $i -lt 90 ]; do
  T=$(python -I -c "print(round($i/30,3))")
  N=$(printf '%03d' $i)
  "/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" --headless --disable-gpu \
    --user-data-dir="D:/dev/tierprofiles/design/_tmp/edge2" --no-first-run \
    --force-device-scale-factor=1 --hide-scrollbars --disable-lcd-text --window-size=1920,1080 \
    --run-all-compositor-stages-before-draw --virtual-time-budget=1500 --allow-file-access-from-files \
    --screenshot="$OUT/f$N.png" "file:///D:/dev/tierprofiles/design/src/v2-01-default-card.html?t=$T" >/dev/null 2>&1
  i=$((i+1))
done
python -I D:/dev/tierprofiles/design/src/make_entrance.py
