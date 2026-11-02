"""Turn the rendered frames into an animated PNG, and a three-frame strip for static viewers."""
from pathlib import Path
from PIL import Image

D = Path(__file__).resolve().parents[1]
files = sorted((D / "_tmp/frames").glob("f*.png"))
frames = [Image.open(f).convert("RGB").crop((0, 0, 752, 256)) for f in files]
frames[0].save(D / "shots/v2-11-moon-card-animated.png", save_all=True, append_images=frames[1:],
               duration=167, loop=0, optimize=True)
print(len(frames), "frames")
