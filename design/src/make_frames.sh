#!/bin/sh
# Render one seamless loop of the showcase card, 48 frames at 6 fps (8 seconds).
OUT=D:/dev/tierprofiles/design/_tmp/frames
mkdir -p /d/dev/tierprofiles/design/_tmp/frames
i=0
while [ $i -lt 48 ]; do
  T=$(python -I -c "print($i/6)")
  N=$(printf '%02d' $i)
  "/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" --headless --disable-gpu \
    --user-data-dir="D:/dev/tierprofiles/design/_tmp/edge4" --no-first-run \
    --force-device-scale-factor=1 --hide-scrollbars --disable-lcd-text --window-size=1920,1080 \
    --run-all-compositor-stages-before-draw --virtual-time-budget=2500 --allow-file-access-from-files \
    --screenshot="$OUT/f$N.png" "file:///D:/dev/tierprofiles/design/src/16-card-live.html?frame=1&t=$T" >/dev/null 2>&1
  i=$((i+1))
done
python -I D:/dev/tierprofiles/design/src/make_anim.py
