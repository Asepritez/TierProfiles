"""Assemble the card entrance frames into an animated PNG."""
from pathlib import Path
from PIL import Image

D = Path(__file__).resolve().parents[1]
files = sorted((D / "_tmp/entrance").glob("f*.png"))
frames = [Image.open(f).convert("RGB").crop((1040, 0, 1920, 300)) for f in files]
frames[0].save(D / "shots/v2-03-card-entrance.png", save_all=True, append_images=frames[1:],
               duration=33, loop=0, optimize=True)
print(len(frames), "frames")
