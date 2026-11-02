"""Assemble rendered frames into animated PNGs. usage: make_v3_anim.py <frames dir> <out> <x y w h> <ms>"""
import sys
from pathlib import Path
from PIL import Image

D = Path(__file__).resolve().parents[1]
src, out = sys.argv[1:3]
x, y, w, h, ms = map(int, sys.argv[3:8])
frames = [Image.open(f).convert("RGB").crop((x, y, x + w, y + h)) for f in sorted((D / "_tmp" / src).glob("f*.png"))]
frames[0].save(D / "shots" / out, save_all=True, append_images=frames[1:], duration=ms, loop=0, optimize=True)
print(len(frames), "frames", out)
