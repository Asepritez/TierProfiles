"""Turn the rendered frames into an animated PNG, and a three-frame strip for static viewers."""
from pathlib import Path
from PIL import Image

D = Path(__file__).resolve().parents[1]
files = sorted((D / "_tmp/frames").glob("f*.png"))
frames = [Image.open(f).convert("RGB").crop((0, 0, 732, 400)) for f in files]
frames[0].save(D / "shots/17-card-moon-animated.png", save_all=True, append_images=frames[1:],
               duration=167, loop=0, optimize=True)
picks = [frames[i] for i in (3, 15, 33)]
strip = Image.new("RGB", (732 * 3 + 24, 400), (9, 11, 12))
for i, im in enumerate(picks):
    strip.paste(im, (i * 744, 0))
strip.save(D / "shots/18-card-moon-frames.png")
print(len(frames), "frames")
