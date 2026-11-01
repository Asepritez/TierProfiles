#!/usr/bin/env python
"""Build mockup assets from the local Minecraft jar and the lists' websites.

Everything this writes goes to design/_tmp/, which is gitignored. Mojang textures and third-party
logos must never be committed. See docs/SPEC.md "Icons and rights" and docs/LOGOS.md.
Run after extracting the jar to design/_tmp/vanilla/cur and downloading to design/_tmp/logos.
"""
import base64, io, json
from pathlib import Path

from PIL import Image

T = Path(__file__).resolve().parents[1] / "_tmp"
CUR = T / "vanilla/cur"
OUT = T / "vanilla/kit"
MARK = T / "logos/marks"
OUT.mkdir(parents=True, exist_ok=True)
MARK.mkdir(parents=True, exist_ok=True)


def item(n):
    return Image.open(CUR / f"item/{n}.png").convert("RGBA")


def tinted_potion(base, rgb):
    """Vanilla potions are a bottle plus a white liquid overlay the game tints."""
    b = item(base)
    o = item("potion_overlay")
    px, q = o.load(), Image.new("RGBA", o.size)
    qp = q.load()
    for y in range(o.height):
        for x in range(o.width):
            r, g, bb, a = px[x, y]
            qp[x, y] = (r * rgb[0] // 255, g * rgb[1] // 255, bb * rgb[2] // 255, a)
    out = Image.new("RGBA", o.size)
    out.alpha_composite(q)
    out.alpha_composite(b)
    return out


def shield():
    """The shield item is a 3D model, so the mockup uses the front of its entity texture."""
    s = Image.open(CUR / "entity/shield/shield_base_nopattern.png").convert("RGBA").crop((1, 1, 13, 23))
    s = s.resize((9, 16), Image.NEAREST)
    o = Image.new("RGBA", (16, 16))
    o.paste(s, (3, 0))
    return o


def official(path, thresh=128):
    """Official art with no flat vanilla item: scale to the 16x16 grid, keep edges crisp."""
    i = Image.open(T / "logos" / path).convert("RGBA")
    i = i.crop(i.getbbox())
    i.thumbnail((16, 16), Image.LANCZOS)
    a = i.split()[3].point(lambda v: 255 if v >= thresh else 0)
    i.putalpha(a)
    o = Image.new("RGBA", (16, 16))
    o.paste(i, ((16 - i.width) // 2, (16 - i.height) // 2))
    return o


KIT = {
    "sword": item("netherite_sword"), "axe": item("netherite_axe"), "mace": item("mace"),
    "spear": item("netherite_spear"), "pot": tinted_potion("splash_potion", (248, 36, 35)),
    "nethop": item("netherite_helmet"), "smp": item("ender_pearl"),
    "uhc": official("mcpvp-kits/uhc.png"), "vanilla": item("end_crystal"), "crystal": item("end_crystal"),
    "shield": shield(), "bow": item("bow"), "cart": item("tnt_minecart"),
    "creeper": item("creeper_spawn_egg"), "diamond_smp": item("diamond_chestplate"),
    "early_game": item("golden_apple"), "late_game": item("netherite_chestplate"),
    "end_game": item("end_crystal"), "trident": item("trident"),
    "bed": official("sub-kits/bed-20260909.png"), "elytra": item("elytra"),
    "speed": tinted_potion("potion", (51, 235, 255)),
}
for k, v in KIT.items():
    v.crop((0, 0, 16, 16)).save(OUT / f"{k}.png")


def mark(src, name, pad=0):
    i = Image.open(T / "logos" / src).convert("RGBA")
    i = i.crop(i.getbbox())
    s = max(i.size)
    c = Image.new("RGBA", (s, s))
    c.paste(i, ((s - i.width) // 2, (s - i.height) // 2))
    for n in (32, 64, 128):
        c.resize((n, n), Image.LANCZOS).save(MARK / f"{name}-{n}.png")


mark("mctiers-logo.png", "mctiers")
mark("mcpvp-apple.png", "mcpvp")
mark("pvptiers.ico", "pvptiers")
mark("subtiers.webp", "subtiers")
mark("pvphq.png", "pvphq")
w = Image.open(T / "logos/mcpvp.png").convert("RGBA")
w.thumbnail((400, 120), Image.LANCZOS)
w.save(MARK / "mcpvp-word.png")

# the moon, as data the page can read without canvas security limits
m = Image.open(CUR / "environment/celestial/moon/full_moon.png").convert("RGBA")
rows = [[list(m.getpixel((x, y))) for x in range(m.width)] for y in range(m.height)]
(T / "vanilla/moon.js").write_text("const MOON_TEX = " + json.dumps(rows, separators=(",", ":")) + ";")
print("kit", len(KIT), "moon", m.size)
