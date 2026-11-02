#!/bin/sh
# usage: make_v3_frames.sh <dir> <profile dir> <frames> <fps> <extra query>
D=$1; EDGE=$2; N=$3; FPS=$4; EXTRA=$5
mkdir -p /d/dev/tierprofiles/design/_tmp/$D
i=0
while [ $i -lt $N ]; do
  T=$(python -I -c "print(round($i/$FPS,3))")
  F=$(printf '%03d' $i)
  "/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" --headless --disable-gpu \
    --user-data-dir="D:/dev/tierprofiles/design/_tmp/$EDGE" --no-first-run \
    --force-device-scale-factor=1 --hide-scrollbars --disable-lcd-text --window-size=1920,1080 \
    --run-all-compositor-stages-before-draw --virtual-time-budget=1500 --allow-file-access-from-files \
    --screenshot="D:/dev/tierprofiles/design/_tmp/$D/f$F.png" "file:///D:/dev/tierprofiles/design/src/v3-01-card.html?t=$T$EXTRA" >/dev/null 2>&1
  i=$((i+1))
done
