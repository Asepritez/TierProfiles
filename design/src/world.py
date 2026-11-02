#!/usr/bin/env python
"""Tiny original voxel renderer for the mockups (numpy + Pillow, no game assets).

It draws blocky worlds with procedural textures, and textured player models with flat-colour
armour, so the drafts look like the game without containing a single Mojang texture.
Skins only come from design/_tmp/skins (gitignored) and are used for mockups.
"""
import json, math, sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TMP = ROOT / "_tmp"
BG = TMP / "bg"
BG.mkdir(parents=True, exist_ok=True)
ICONS = TMP / "vanilla/kit"
F = np.float32


# ---------------------------------------------------------------- procedural block textures
def tile(fn, n=4, seed=1):
    rng = np.random.default_rng(seed)
    return np.stack([fn(rng) for _ in range(n)]).astype(F) / 255


def noise_tile(rng, base, spread, specks=()):
    a = np.array(base, F) + rng.integers(-spread, spread + 1, (16, 16, 1))
    for col, chance in specks:
        m = rng.random((16, 16)) < chance
        a[m] = col
    return np.clip(a, 0, 255)


def t_grass(r):
    return noise_tile(r, (92, 172, 60), 16, [((70, 146, 48), .22), ((122, 196, 74), .12)])


def t_grass_side(r):
    a = noise_tile(r, (126, 88, 56), 12, [((98, 68, 44), .2)])
    g = noise_tile(r, (92, 172, 60), 14)
    for x in range(16):
        h = 3 + r.integers(0, 3)
        a[:h, x] = g[:h, x]
    return a


def t_dirt(r):
    return noise_tile(r, (126, 88, 56), 12, [((98, 68, 44), .2)])


def t_brick(r):
    a = noise_tile(r, (142, 146, 154), 7)
    m = np.array((94, 98, 108), F)
    a[7:8, :] = m; a[15:16, :] = m
    a[0:7, 7] = m; a[8:15, 15] = m
    a[0, :7] += 16; a[8, :15] += 16
    return np.clip(a, 0, 255)


def t_tile(r):
    a = noise_tile(r, (226, 222, 214), 4)
    a[15, :] = (188, 184, 176); a[:, 15] = (188, 184, 176)
    a[0, :] = (240, 237, 230); a[:, 0] = (240, 237, 230)
    return a


def t_dark(r):
    a = noise_tile(r, (52, 56, 70), 4, [((44, 47, 60), .15)])
    a[15, :] = (34, 36, 46); a[:, 15] = (34, 36, 46)
    return a


def t_plank(r):
    a = noise_tile(r, (176, 132, 78), 8)
    for y in (3, 7, 11, 15):
        a[y, :] = (122, 88, 48)
    return a


def t_bark(r):
    a = noise_tile(r, (96, 70, 44), 10)
    for x in r.integers(0, 16, 5):
        a[:, x] = (72, 52, 32)
    return a


def t_leaf(r):
    return noise_tile(r, (48, 128, 52), 18, [((30, 94, 40), .35), ((74, 156, 66), .18)])


def t_obsidian(r):
    return noise_tile(r, (40, 28, 64), 8, [((70, 48, 110), .1), ((26, 18, 42), .25)])


def t_sand(r):
    return noise_tile(r, (226, 210, 160), 8)


def t_water(r):
    return noise_tile(r, (64, 130, 214), 10)


def t_accent(r):
    a = noise_tile(r, (104, 181, 213), 5)
    a[15, :] = (64, 128, 158); a[:, 15] = (64, 128, 158)
    return a


VAN = TMP / "vanilla/cur"
GRASS = np.array((0x91, 0xBD, 0x59), F) / 255
LEAF = np.array((0x48, 0xB5, 0x18), F) / 255


def block(name, tint=None, base=None):
    im = np.asarray(Image.open(VAN / f"block/{name}.png").convert("RGBA")).astype(F)[:16, :16] / 255
    rgb, al = im[..., :3], im[..., 3:]
    if tint is not None:
        rgb = rgb * tint
    if base is not None:
        rgb = base * (1 - al) + rgb * al
    return rgb


def mat(top, side=None, bottom=None):
    side = top if side is None else side
    bottom = top if bottom is None else bottom
    return np.stack([np.stack([x] * 4) for x in (top, side, bottom)])


_dirt = block("dirt")
_side = block("grass_block_side_overlay", GRASS, block("grass_block_side"))
_leaf = block("oak_leaves", LEAF, np.array((.05, .13, .05), F))
MATS = {"grass": mat(block("grass_block_top", GRASS), _side, _dirt), "dirt": mat(_dirt),
        "brick": mat(block("stone_bricks")), "tile": mat(block("smooth_stone")),
        "dark": mat(block("deepslate_tiles")), "plank": mat(block("oak_planks")),
        "bark": mat(block("oak_log_top"), block("oak_log"), block("oak_log_top")), "leaf": mat(_leaf),
        "obsidian": mat(block("obsidian")), "sand": mat(block("sand")), "water": mat(block("sand")),
        "accent": mat(block("prismarine_bricks"))}
MAT_ID = {k: i for i, k in enumerate(MATS)}
ATLAS = np.stack(list(MATS.values()))  # mat, top/side/bottom, var, y, x, rgb


# ---------------------------------------------------------------- player model
BOXES = {  # name: (w, h, d, base uv, overlay uv, pivot y, x offset)
    "head": (8, 8, 8, (0, 0), (32, 0)),
    "body": (8, 12, 4, (16, 16), (16, 32)),
    "rarm": (4, 12, 4, (40, 16), (40, 32)),
    "larm": (4, 12, 4, (32, 48), (48, 48)),
    "rleg": (4, 12, 4, (0, 16), (0, 32)),
    "lleg": (4, 12, 4, (16, 48), (0, 48)),
}


def face_rects(uv, w, h, d):
    x, y = uv
    return {  # local face -> (u, v, du, dv)
        "py": (x + d, y, w, d), "ny": (x + d + w, y, w, d),
        "nx": (x, y + d, d, h), "pz": (x + d, y + d, w, h),
        "px": (x + d + w, y + d, d, h), "nz": (x + d + w + d, y + d, w, h),
    }


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], F)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], F)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], F)


class Part:
    def __init__(self, C, R, half, tex, rects, emissive=False):
        self.C, self.R, self.h, self.tex, self.rects = np.array(C, F), R, np.array(half, F), tex, rects
        self.emissive = emissive


def load_skin(name):
    im = Image.open(TMP / "skins" / f"{name}.png").convert("RGBA")
    return np.asarray(im).astype(F) / 255


def is_slim(skin):
    return skin[20, 54, 3] == 0 if skin.shape[0] == 64 else False


def player_parts(skin, pos, yaw=0.0, pose=None, overlay=None, scale=1 / 16 * .9375, head_pitch=0.0,
                 head_yaw=0.0):
    """Return the Parts for one player. pose gives limb swing in radians."""
    p = dict(rarm=0, larm=0, rleg=0, lleg=0, rarm_z=0, larm_z=0)
    if pose:
        p.update(pose)
    Y = rot_y(yaw)
    px, py, pz = pos
    s = scale
    out = []
    slim = is_slim(skin)

    def add(name, cx, cy, pivot_top, swing=0.0, roll=0.0, extra=None):
        w, h, d, uv, ouv = BOXES[name]
        if name in ("rarm", "larm") and slim:
            w = 3
        R = Y @ rot_x(swing) @ rot_z(roll)
        if name == "head":
            R = Y @ rot_y(head_yaw) @ rot_x(head_pitch)
        # limb rotates around the pivot at its top
        piv = np.array([cx, pivot_top, 0], F)
        c_local = np.array([0, -(h / 2), 0], F) if name != "head" else np.array([0, h / 2, 0], F)
        c = Y @ piv + (R @ c_local) / 1
        C = np.array([px, py, pz], F) + c * s
        hsz = np.array([w / 2, h / 2, d / 2], F) * s
        out.append(Part(C, R, hsz, skin, face_rects(uv, w, h, d)))
        out.append(Part(C, R, hsz + .5 * s, skin, face_rects(ouv, w, h, d)))
        for k, (tex, auv, inf) in enumerate((overlay or {}).get(name, [])):
            aw = 4 if name in ("rarm", "larm") else w
            out.append(Part(C, R, np.array([aw / 2, h / 2, d / 2], F) * s + (inf + k * .02) * s, tex,
                            face_rects(auv, aw, h, d)))

    ax = 6 if not slim else 5.5
    add("rleg", -2, 12, 12, p["rleg"])
    add("lleg", 2, 12, 12, p["lleg"])
    add("body", 0, 24, 24)
    add("rarm", -ax, 24, 24, p["rarm"], p["rarm_z"])
    add("larm", ax, 24, 24, p["larm"], p["larm_z"])
    add("head", 0, 24, 24)
    return out


# ---------------------------------------------------------------- generated skins and armour
HAIR = [(40, 28, 20), (86, 58, 32), (196, 160, 80), (30, 30, 34), (140, 60, 36), (220, 220, 230)]
TONE = [(240, 200, 170), (208, 160, 124), (150, 104, 76), (98, 66, 48)]
CLOTH = [(214, 58, 58), (54, 112, 214), (52, 170, 92), (232, 176, 44), (150, 70, 200), (40, 42, 52),
         (232, 232, 236), (240, 120, 40), (44, 170, 180), (216, 80, 150)]


def make_skin(seed):
    """A simple original skin in the standard 64x64 layout."""
    r = np.random.default_rng(seed)
    a = np.zeros((64, 64, 4), np.uint8)
    tone = TONE[r.integers(len(TONE))]
    hair = HAIR[r.integers(len(HAIR))]
    shirt = CLOTH[r.integers(len(CLOTH))]
    pants = CLOTH[r.integers(len(CLOTH))]
    shoes = (34, 34, 40)

    def paint(uv, w, h, d, col):
        for (u, v, du, dv) in face_rects(uv, w, h, d).values():
            a[v:v + dv, u:u + du] = (*col, 255)

    paint((0, 0), 8, 8, 8, tone)
    paint((16, 16), 8, 12, 4, shirt)
    for uv in ((40, 16), (32, 48)):
        paint(uv, 4, 12, 4, shirt)
        a[28:32, uv[0]:uv[0] + 16] = (*tone, 255) if uv[1] == 16 else a[28:32, uv[0]:uv[0] + 16]
    a[60:64, 32:48] = (*tone, 255)
    for uv in ((0, 16), (16, 48)):
        paint(uv, 4, 12, 4, pants)
    a[29:32, 0:16] = (*shoes, 255); a[61:64, 16:32] = (*shoes, 255)
    # hair: top, back and a fringe on every side
    a[0:8, 8:16] = (*hair, 255)
    a[8:8 + 2 + r.integers(0, 2), 0:32] = (*hair, 255)
    a[8:16, 24:32] = (*hair, 255)
    # face
    a[12, 9:11] = (250, 250, 250, 255); a[12, 13:15] = (250, 250, 250, 255)
    eye = [(50, 90, 200), (60, 130, 70), (70, 44, 30)][r.integers(3)]
    a[12, 10] = (*eye, 255); a[12, 13] = (*eye, 255)
    a[14, 11:13] = (150, 90, 80, 255)
    return a.astype(F) / 255


ARM_FILE = {"chain": "chainmail"}
TRIM = {"gold": (255, 214, 64), "amethyst": (190, 110, 255), "redstone": (240, 50, 50),
        "lapis": (50, 96, 230), "emerald": (40, 220, 110), "copper": (232, 130, 80),
        "quartz": (240, 236, 226), "diamond": (110, 240, 240), "netherite": (60, 52, 60)}


def _armor_tex(sub, m):
    im = Image.open(VAN / f"entity/equipment/{sub}/{ARM_FILE.get(m, m)}.png").convert("RGBA")
    return np.asarray(im).astype(F) / 255


def _trim_tex(sub, pattern, material):
    a = np.asarray(Image.open(VAN / f"trims/entity/{sub}/{pattern}.png").convert("RGBA")).astype(F) / 255
    c = np.array(TRIM[material], F) / 255
    lum = a[..., :3].mean(-1, keepdims=True) * 1.5
    return np.dstack([np.clip(lum * c, 0, 1), a[..., 3]])


def armor_overlay(slots, pattern=None, trim=None):
    """Real vanilla armor and trim textures, laid out by body part as the game does."""
    def layers(sub, m):
        out = [_armor_tex(sub, m)]
        if pattern and trim:
            out.append(_trim_tex(sub, pattern, trim))
        return out
    A = {}
    if slots.get("head"):
        A["head"] = [(t, (0, 0), 1.0) for t in layers("humanoid", slots["head"])]
    if slots.get("chest"):
        c = layers("humanoid", slots["chest"])
        A["body"] = [(t, (16, 16), 1.0) for t in c]
        A["rarm"] = A["larm"] = [(t, (40, 16), 1.0) for t in c]
    legs = []
    if slots.get("legs"):
        l = layers("humanoid_leggings", slots["legs"])
        A.setdefault("body", [])
        A["body"] = [(t, (16, 16), .5) for t in l] + A["body"]
        legs += [(t, (0, 16), .5) for t in l]
    if slots.get("feet"):
        legs += [(t, (0, 16), 1.0) for t in layers("humanoid", slots["feet"])]
    if legs:
        A["rleg"] = A["lleg"] = legs
    return A


# ---------------------------------------------------------------- camera and ray casting
class Cam:
    def __init__(self, W, H, pos, yaw, pitch, fov=70):
        self.W, self.H = W, H
        self.pos = np.array(pos, F)
        cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
        self.f = np.array([sy * cp, sp, -cy * cp], F)
        self.r = np.array([cy, 0, sy], F)
        self.u = np.cross(self.r, self.f)
        self.tan = math.tan(math.radians(fov) / 2)
        xs = (np.arange(W, dtype=F) + .5) / H * 2 - W / H
        ys = 1 - (np.arange(H, dtype=F) + .5) / H * 2
        xx, yy = np.meshgrid(xs, ys)
        d = self.f[None, None] + self.r[None, None] * (xx * self.tan)[..., None] + self.u[None, None] * (yy * self.tan)[..., None]
        self.d = d / np.linalg.norm(d, axis=-1, keepdims=True)

    def project(self, p):
        v = np.array(p, F) - self.pos
        z = float(v @ self.f)
        if z <= .01:
            return None
        x = float(v @ self.r) / z / self.tan
        y = float(v @ self.u) / z / self.tan
        return (x * self.H / 2 + self.W / 2, self.H / 2 - y * self.H / 2, z)

    def basis(self):
        # camera space: x right, y up, z towards the viewer
        return np.stack([self.r, self.u, -self.f], 1)


def rect_of(cam, corners):
    pts = [cam.project(c) for c in corners]
    if any(p is None for p in pts):
        return 0, cam.W, 0, cam.H
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    return (max(0, int(min(xs)) - 2), min(cam.W, int(max(xs)) + 3),
            max(0, int(min(ys)) - 2), min(cam.H, int(max(ys)) + 3))


def corners(C, R, h):
    out = []
    for sx in (-1, 1):
        for sy in (-1, 1):
            for sz in (-1, 1):
                out.append(C + R @ (h * np.array([sx, sy, sz], F)))
    return out


def trace_box(o, d, lo, hi):
    with np.errstate(divide="ignore", invalid="ignore"):
        inv = 1 / d
        t0 = (lo - o) * inv
        t1 = (hi - o) * inv
    tmin = np.minimum(t0, t1)
    tmax = np.maximum(t0, t1)
    near = tmin.max(-1)
    far = tmax.min(-1)
    hit = (near < far) & (far > 0) & (near > 0)
    return hit, near, tmin.argmax(-1)


class Scene:
    def __init__(self, cam, sky=((122, 170, 255), (196, 222, 255)), fog=90, clouds=True):
        self.cam = cam
        H, W = cam.H, cam.W
        self.t = np.full((H, W), 1e9, F)
        self.rgb = np.zeros((H, W, 3), F)
        self.alpha = np.zeros((H, W), F)
        self.boxes = []
        self.shadows = []
        self.sky, self.fogd, self.clouds = sky, fog, clouds

    def box(self, lo, hi, mat):
        self.boxes.append((np.array(lo, F), np.array(hi, F), MAT_ID[mat]))

    def draw_boxes(self):
        cam = self.cam
        H, W = cam.H, cam.W
        o = cam.pos
        d = cam.d
        ht = np.full((H, W), 1e9, F)
        mat = np.zeros((H, W), np.int32)
        ax = np.zeros((H, W), np.int32)
        for lo, hi, m in self.boxes:
            hit, near, a = trace_box(o, d, lo, hi)
            upd = hit & (near < ht)
            ht[upd] = near[upd]; mat[upd] = m; ax[upd] = a[upd]
        ok = ht < 1e8
        p = o + d * ht[..., None]
        p = p + d * 1e-3
        # uv per hit face in world block space
        u = np.where(ax == 0, p[..., 2], p[..., 0])
        v = np.where(ax == 1, p[..., 2], p[..., 1])
        bx = np.floor(p).astype(np.int32)
        ui = np.floor((u - np.floor(u)) * 16).astype(np.int32).clip(0, 15)
        vi = np.floor((v - np.floor(v)) * 16).astype(np.int32).clip(0, 15)
        vi = np.where(ax == 1, vi, 15 - vi)
        var = ((bx[..., 0] * 73856093) ^ (bx[..., 1] * 19349663) ^ (bx[..., 2] * 83492791)) % 4
        kind = np.where(ax == 1, np.where(d[..., 1] < 0, 0, 2), 1)
        col = ATLAS[mat, kind, var, vi, ui]
        shade = np.where(ax == 1, np.where(d[..., 1] < 0, 1.0, .5), np.where(ax == 2, .8, .62))
        self.t[ok] = ht[ok]
        self.rgb[ok] = (col * shade[..., None])[ok]
        self.alpha[ok] = 1
        self.world_p = p

    def draw_parts(self, parts):
        cam = self.cam
        for prt in parts:
            x0, x1, y0, y1 = rect_of(cam, corners(prt.C, prt.R, prt.h))
            if x1 <= x0 or y1 <= y0:
                continue
            d = cam.d[y0:y1, x0:x1]
            Rt = prt.R.T
            lo_d = d @ prt.R  # same as Rt applied to each vector
            lo_o = Rt @ (cam.pos - prt.C)
            hit, near, a = trace_box(lo_o, lo_d, -prt.h, prt.h)
            cur = self.t[y0:y1, x0:x1]
            hit &= near < cur
            if not hit.any():
                continue
            P = lo_o + lo_d * near[..., None]
            q = (P + prt.h) / (2 * prt.h)  # 0..1 along each local axis
            sign = np.take_along_axis(lo_d, a[..., None], -1)[..., 0] < 0  # True = positive face
            H, W = hit.shape
            uu = np.zeros((H, W), F); vv = np.zeros((H, W), F)
            sel = {
                "px": (a == 0) & sign, "nx": (a == 0) & ~sign,
                "py": (a == 1) & sign, "ny": (a == 1) & ~sign,
                "pz": (a == 2) & sign, "nz": (a == 2) & ~sign,
            }
            for key, m in sel.items():
                if key not in prt.rects:
                    hit[m] = False
                    continue
                r0, r1, du, dv = prt.rects[key]
                if key == "pz":
                    s, t = q[..., 0], 1 - q[..., 1]
                elif key == "nz":
                    s, t = 1 - q[..., 0], 1 - q[..., 1]
                elif key == "nx":
                    s, t = q[..., 2], 1 - q[..., 1]
                elif key == "px":
                    s, t = 1 - q[..., 2], 1 - q[..., 1]
                elif key == "py":
                    s, t = q[..., 0], q[..., 2]
                else:
                    s, t = q[..., 0], 1 - q[..., 2]
                uu = np.where(m, r0 + np.minimum(s, .9999) * du, uu)
                vv = np.where(m, r1 + np.minimum(t, .9999) * dv, vv)
            ui = uu.astype(np.int32).clip(0, prt.tex.shape[1] - 1)
            vi = vv.astype(np.int32).clip(0, prt.tex.shape[0] - 1)
            texel = prt.tex[vi, ui]
            vis = hit & (texel[..., 3] > .5)
            if not vis.any():
                continue
            # local normal -> world, for model lighting
            n = np.zeros((H, W, 3), F)
            s = np.where(sign, 1.0, -1.0)
            n[np.arange(H)[:, None], np.arange(W)[None], a] = s
            nw = n @ prt.R.T
            L = np.array([.35, .8, .5], F); L /= np.linalg.norm(L)
            b = .66 + .42 * np.clip(nw @ L, 0, 1)
            if prt.emissive:
                b = b * 0 + 1
            col = texel[..., :3] * b[..., None]
            rgb = self.rgb[y0:y1, x0:x1]
            rgb[vis] = col[vis]
            cur[vis] = near[vis]
            self.alpha[y0:y1, x0:x1][vis] = 1

    def blob(self, x, z, y=0.0, r=.6, strength=.45):
        self.shadows.append((x, z, y, r, strength))

    def finish(self):
        cam = self.cam
        ok = self.alpha > 0
        d = cam.d
        p = cam.pos + d * self.t[..., None]
        for x, z, y, r, s in self.shadows:
            dist = np.hypot(p[..., 0] - x, p[..., 2] - z)
            m = (np.abs(p[..., 1] - y) < .02) & (dist < r)
            k = 1 - s * np.clip(1.2 - dist / r, 0, 1)
            self.rgb[m] *= k[m][..., None]
        # sky
        k = np.clip(d[..., 1] * 3, 0, 1)[..., None]
        low = np.array(self.sky[1], F) / 255; high = np.array(self.sky[0], F) / 255
        sky = low * (1 - k) + high * k
        if self.clouds:
            with np.errstate(divide="ignore", invalid="ignore"):
                t = (92 - cam.pos[1]) / d[..., 1]
            cx = cam.pos[0] + d[..., 0] * t
            cz = cam.pos[2] + d[..., 2] * t
            ci = np.floor(cx / 12).astype(np.int64)
            cj = np.floor(cz / 12).astype(np.int64)
            cm = np.asarray(Image.open(VAN / "environment/clouds.png").convert("RGBA"))[..., 3] > 0
            c = cm[cj % cm.shape[0], ci % cm.shape[1]] & (d[..., 1] > .02) & (t > 0) & (t < 4000)
            fade = np.clip(1 - t / 2200, 0, 1)
            a = (c * .9 * fade)[..., None]
            sky = sky * (1 - a) + np.array((.98, .98, 1.0), F) * a
        # fog
        fog = np.clip((self.t - self.fogd * .35) / (self.fogd * .65), 0, 1)[..., None] ** 1.5
        rgb = self.rgb * (1 - fog) + low * fog
        rgb = np.where(ok[..., None], rgb, sky)
        return (np.clip(rgb, 0, 1) * 255 + .5).astype(np.uint8)


def save(rgb, name):
    Image.fromarray(rgb).save(BG / f"{name}.png")
    print("wrote", name)


def icon_tex(name):
    return np.asarray(Image.open(ICONS / f"{name}.png").convert("RGBA")).astype(F) / 255


def held_item(cam, name, skin):
    """First-person right arm and a flat icon item, in camera space."""
    B = cam.basis()
    parts = []
    # arm
    Ra = B @ rot_y(math.radians(-10)) @ rot_x(math.radians(-64)) @ rot_z(math.radians(-22))
    Ca = cam.pos + B @ np.array([.80, -.74, -1.05], F)
    w, h, d, uv, ouv = BOXES["rarm"]
    s = 1 / 16 * .8
    parts.append(Part(Ca, Ra, np.array([w / 2, h / 2, d / 2], F) * s, skin, face_rects(uv, w, h, d)))
    parts.append(Part(Ca, Ra, (np.array([w / 2, h / 2, d / 2], F) + .4) * s, skin, face_rects(ouv, w, h, d)))
    # item: a thin slab carrying the icon on both faces
    tex = icon_tex(name)[:, ::-1]  # mirrored: grip sits by the hand, blade leans in
    Ri = B @ rot_y(math.radians(24)) @ rot_x(math.radians(-10)) @ rot_z(math.radians(-8))
    Ci = cam.pos + B @ np.array([.60, -.34, -1.10], F)
    rects = {"pz": (0, 0, 16, 16), "nz": (0, 0, 16, 16)}
    parts.append(Part(Ci, Ri, np.array([.2, .2, .004], F), tex, rects, emissive=True))
    return parts


# ---------------------------------------------------------------- scenes
def arena_ground(sc, y=0, r=70):
    sc.box((-r, y - 3, -r), (r, y, r), "grass")


def tree(sc, x, z, h=5, y=0):
    sc.box((x, y, z), (x + 1, y + h, z + 1), "bark")
    sc.box((x - 2, y + h - 2, z - 2), (x + 3, y + h, z + 3), "leaf")
    sc.box((x - 1, y + h, z - 1), (x + 2, y + h + 2, z + 2), "leaf")


def arena_walls(sc, x0=-14, x1=14, z0=-16, z1=8):
    sc.box((x0, 0, z0), (x1, 2, z0 + 1), "brick")
    sc.box((x0, 0, z1 - 1), (x1, 2, z1), "brick")
    sc.box((x0, 0, z0), (x0 + 1, 2, z1), "brick")
    sc.box((x1 - 1, 0, z0), (x1, 2, z1), "brick")


def scene_fight():
    cam = Cam(1920, 1080, (0.0, 1.62, 3.2), math.radians(-4), math.radians(-3))
    sc = Scene(cam)
    arena_ground(sc)
    sc.box((-14, 0, -16), (14, .02, 8), "brick")
    arena_walls(sc)
    for x, z, h in ((-22, -24, 6), (-16, -30, 7), (-6, -28, 6), (5, -34, 7), (14, -26, 6), (24, -30, 7),
                    (-30, -14, 6), (31, -12, 6), (20, -20, 5)):
        tree(sc, x, z, h)
    sc.box((-60, 0, -60), (-30, 5, -44), "grass")
    sc.box((-44, 5, -60), (-30, 9, -48), "grass")
    sc.box((34, 0, -62), (64, 7, -46), "grass")
    sc.box((44, 7, -62), (64, 11, -50), "grass")
    sc.draw_boxes()
    foe = make_skin(5)
    arm = armor_overlay(dict(head="diamond", chest="diamond", legs="diamond", feet="diamond"))
    swing = dict(rarm=math.radians(-70), larm=math.radians(12), rleg=math.radians(22), lleg=math.radians(-22))
    sc.draw_parts(player_parts(foe, (-.25, 0.02, -.1), yaw=math.radians(10), pose=swing, overlay=arm,
                               head_pitch=math.radians(6)))
    sc.blob(-.25, -.1, .02, .9, .5)
    far = make_skin(9)
    arm2 = armor_overlay(dict(head="netherite", chest="netherite", legs="netherite", feet="netherite"))
    pose2 = dict(rarm=math.radians(-30), larm=math.radians(24), rleg=math.radians(-20), lleg=math.radians(20))
    sc.draw_parts(player_parts(far, (6.2, 0.02, -9.0), yaw=math.radians(-56), pose=pose2, overlay=arm2))
    sc.blob(6.2, -9, .02, .8, .4)
    sc.draw_parts(held_item(cam, "sword", load_skin("JokingDK")))
    save(sc.finish(), "fight")


LOBBY_NAMES = ["KitSmith", "PearlPanic", "Bedwrecker", "lowroll_", "Maceling", "CrystalKid", "OneTap_Oli",
               "ShieldNan", "Totemless", "ByteBow", "TacoStrafe", "Wtapper", "SoupGhost", "NoHitNora",
               "PotDrinker", "GappleGus", "DripCart", "FenceJump", "SpearSam", "ElytraEd"]


def scene_lobby():
    cam = Cam(1920, 1080, (0.0, 7.6, 16.0), 0.0, math.radians(-24), 58)
    sc = Scene(cam, sky=((110, 160, 248), (190, 216, 250)), fog=120)
    arena_ground(sc)
    for x in range(-26, 26, 2):
        for z in range(-26, 20, 2):
            m = "brick" if (x // 2 + z // 2) % 8 else "tile"
            sc.box((x, 0, z), (x + 2, .02, z + 2), m)
    for x in (-21, 21):
        for z in (-20, -10, 0):
            sc.box((x - 1, 0, z - 1), (x + 1, 7, z + 1), "tile")
            sc.box((x - 1.5, 7, z - 1.5), (x + 1.5, 8, z + 1.5), "accent")
    sc.box((-26, 0, -28), (26, 4, -26), "dark")
    sc.box((-26, 4, -28), (26, 5, -26), "accent")
    sc.box((-6, 4, -28), (6, 12, -26), "dark")
    for x, z in ((-24, -32), (-16, -36), (16, -34), (26, -30), (0, -40)):
        tree(sc, x, z, 7)
    sc.draw_boxes()
    r = np.random.default_rng(12)
    spots = [(-5.2, 8.6), (3.4, 9.2), (-8.6, 3.2), (-1.6, 2.6), (5.2, 3.6), (11.4, 2.8), (-10.8, -2.2), (-3.6, -1.6),
             (3.6, -2.4), (10.4, -1.4), (-13.2, -7.8), (-6.2, -8.2), (1.2, -7.4), (7.6, -8.4), (14.2, -7.2),
             (-16.4, -14.4), (-8.2, -13.6), (.4, -14.2), (8.4, -13.6), (16.6, -14.6)]
    players = []
    for i, (x, z) in enumerate(spots):
        skin = make_skin(100 + i)
        arm = None
        if i % 3 == 0:
            mat = ["diamond", "netherite", "iron"][(i // 3) % 3]
            arm = armor_overlay(dict(head=mat, chest=mat, legs=mat, feet=mat))
        yaw = float(r.uniform(-2.2, 2.2))
        sw = float(r.uniform(.15, .45)) * (1 if i % 2 else -1)
        pose = dict(rarm=sw, larm=-sw, rleg=-sw, lleg=sw) if i % 4 else None
        sc.draw_parts(player_parts(skin, (x, .02, z), yaw=yaw, pose=pose, overlay=arm))
        sc.blob(x, z, .02, .8, .4)
        top = cam.project((x, 2.15, z))
        players.append(dict(name=LOBBY_NAMES[i], x=round(top[0]), y=round(top[1]), d=round(top[2], 1)))
    save(sc.finish(), "lobby")
    (BG / "lobby.js").write_text("const LOBBY = " + json.dumps(players) + ";")


def scene_stage():
    """The Live Stage: dark dusk, so it sits quietly beside the settings."""
    cam = Cam(732, 528, (0.0, 1.5, 5.2), 0.0, math.radians(-2), 56)
    sc = Scene(cam, sky=((26, 34, 62), (82, 76, 110)), fog=60, clouds=False)
    arena_ground(sc)
    sc.box((-30, 0, -9), (30, 2, -8), "brick")
    for x, z, h in ((-9, -13, 6), (-3, -15, 7), (4, -14, 6), (10, -13, 7)):
        tree(sc, x, z, h)
    sc.draw_boxes()
    sc.draw_parts(player_parts(load_skin("JokingDK"), (0, 0, 0), yaw=-.35, pose=dict(rarm=.1, larm=-.1, rleg=.08, lleg=-.08)))
    sc.blob(0, 0, 0, .8, .4)
    rgb = sc.finish()
    Image.fromarray((rgb * .62).astype(np.uint8)).save(BG / "stage.png")
    top = cam.project((0, 2.15, 0))
    (BG / "stage.js").write_text(f"const STAGE_POS = {{x:{top[0]/732:.3f},y:{top[1]/528:.3f}}};")
    print("wrote stage", top)


def model(name, skin, overlay=None, yaw=-.5, pitch=-.16, W=360, H=640, zoom=1.0, pose=None, head_yaw=.25,
          head_pitch=0.0, supersample=1, sky=False):
    """Character render on transparent background, in a near-orthographic 3/4 view."""
    dist = 60
    fov = 2 * math.degrees(math.atan(1.25 / zoom / dist))
    cam = Cam(W, H, (0, .95 + dist * math.sin(-pitch), dist * math.cos(pitch)), 0.0, pitch, fov)
    sc = Scene(cam, clouds=False)
    sc.draw_parts(player_parts(skin, (0, 0, 0), yaw=yaw, overlay=overlay,
                               pose=pose or dict(rarm=.08, larm=-.08, rarm_z=-.08, larm_z=.08, rleg=.1, lleg=-.1),
                               head_yaw=head_yaw, head_pitch=head_pitch))
    a = np.clip(sc.rgb, 0, 1)
    out = np.dstack([(a * 255 + .5).astype(np.uint8), (sc.alpha * 255).astype(np.uint8)])
    im = Image.fromarray(out, "RGBA")
    im.crop(im.getbbox()).save(BG / f"{name}.png")
    print("wrote", name)


def models():
    jok = load_skin("JokingDK")
    mar = load_skin("Marlowww")
    model("m-joking", jok, yaw=-.5, W=240, H=290)
    model("m-marlowww", mar, yaw=-.5, W=240, H=290)
    model("m-joking-lg", jok, yaw=-.5, W=360, H=440)
    model("m-marlowww-lg", mar, yaw=-.5, W=360, H=440)
    model("m-joking-front", jok, yaw=-.25, head_yaw=.1, W=480, H=600)
    a = armor_overlay(dict(head="netherite", chest="diamond", legs="netherite", feet="diamond"), "rib", "amethyst")
    model("m-joking-armor", jok, overlay=a, yaw=-.55, W=720, H=1280)
    show = armor_overlay(dict(head="netherite", chest="netherite", legs="netherite", feet="netherite"), "coast", "amethyst")
    model("m-armor-hero", jok, overlay=show, yaw=-.5, W=400, H=395)
    model("m-armor-card", jok, overlay=show, yaw=-.5, W=240, H=290)
    model("m-armor-sm", jok, overlay=show, yaw=-.5, W=220, H=176)
    model("m-armor-lg", jok, overlay=show, yaw=-.5, W=460, H=575)
    for i, yaw in enumerate((-.55, .55, 3.14)):
        model(f"m-rot{i}", jok, overlay=a, yaw=yaw, W=360, H=640)


if __name__ == "__main__":
    what = sys.argv[1:] or ["fight", "lobby", "models"]
    for w in what:
        globals()["scene_" + w]() if w in ("fight", "lobby", "stage") else models()
