#!/usr/bin/env python
"""Build a TTF from the game's own ascii.png, into gitignored design/_tmp/fonts/, and measure strings.

The mockups then use the real glyphs and the real proportional widths, so column widths measured
here hold in game. 1 font pixel = 100 units and 1 em = 9 pixels, so font-size 18px draws at 2 real
pixels per font pixel, the same as GUI scale 2.
"""
import json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "_tmp/py"))
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from PIL import Image

T = Path(__file__).resolve().parents[1] / "_tmp"
A = Image.open(T / "vanilla/cur/font/ascii.png").convert("RGBA")


def cell(ch):
    c = ord(ch)
    x, y = (c % 16) * 8, (c // 16) * 8
    return [[A.getpixel((x + i, y + j))[3] > 0 for i in range(8)] for j in range(8)]


def width(g):
    w = 0
    for row in g:
        for i, v in enumerate(row):
            if v:
                w = max(w, i + 1)
    return w


def glyph(g, bold):
    pen = TTGlyphPen(None)
    rows = g
    if bold:
        rows = [[(r[i] or (i > 0 and r[i - 1])) for i in range(8)] + [r[7]] for r in g]
    for j, row in enumerate(rows):
        i = 0
        while i < len(row):
            if row[i]:
                s = i
                while i < len(row) and row[i]:
                    i += 1
                x0, x1 = s * 100, i * 100
                y0, y1 = (6 - j) * 100, (7 - j) * 100
                pen.moveTo((x0, y1)); pen.lineTo((x1, y1)); pen.lineTo((x1, y0)); pen.lineTo((x0, y0))
                pen.closePath()
            else:
                i += 1
    return pen.glyph()


def build(path, name, bold):
    order = [".notdef", "space"]
    cmap = {32: "space"}
    pen = TTGlyphPen(None)
    glyphs = {".notdef": pen.glyph(), "space": TTGlyphPen(None).glyph()}
    metrics = {".notdef": (400, 0), "space": (400 + (100 if bold else 0), 0)}
    for c in range(33, 127):
        n = f"u{c:04X}"
        g = cell(chr(c))
        order.append(n)
        cmap[c] = n
        glyphs[n] = glyph(g, bold)
        metrics[n] = ((width(g) + 1 + (1 if bold else 0)) * 100, 0)
    fb = FontBuilder(900, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=800, descent=-100)
    fb.setupNameTable({"familyName": name, "styleName": "Regular"})
    fb.setupOS2(sTypoAscender=800, sTypoDescender=-100, usWinAscent=800, usWinDescent=100)
    fb.setupPost()
    fb.save(path)


def text_w(s, bold=False):
    w = 0
    for ch in s:
        w += 4 if ch == " " else width(cell(ch)) + 1
        if bold:
            w += 1
    return w - 1  # no gap after the last glyph


(T / "fonts").mkdir(exist_ok=True)
build(T / "fonts/mc.ttf", "McMock", False)
build(T / "fonts/mc-bold.ttf", "McMockBold", True)

months = "Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec".split()
chips = [a + b for a in ("HT", "MT", "LT", "ST") for b in "123456"]
names = ["MCTiers", "McPvP", "PvPTiers", "SubTiers", "PVPHQ", "McPvP beta"]
out = {
    "chips": max(text_w(c) for c in chips), "chip_widest": max(chips, key=text_w),
    "date_full": max(text_w(f"{d} {m} 2026") for d in ("28", "22", "20", "30", "8") for m in months),
    "date_month": max(text_w(f"{m} 2026") for m in months),
    "names": {n: text_w(n) for n in names}, "unranked": text_w("Unranked"),
    "points": text_w("points"), "digits": text_w("0"),
}
print(json.dumps(out, indent=1))
