#!/usr/bin/env python3
"""Renders the WYRIX store icons and graphics (W-in-a-ring mark). Needs Pillow + numpy.
Usage: python3 tools/make-icons.py   ->  store-assets/play/*.png, store-assets/amazon/*.png"""
import os, math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.join(os.path.dirname(__file__), "..", "store-assets")
BLUE, VIOLET = (91, 140, 255), (155, 107, 255)
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

def gradient(size, c1, c2, angle_deg=135):
    w, h = size
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    a = math.radians(angle_deg)
    t = (xx * math.cos(a) + yy * math.sin(a))
    t = (t - t.min()) / (t.max() - t.min() + 1e-6)
    arr = np.zeros((h, w, 3), np.float32)
    for i in range(3): arr[..., i] = c1[i] + (c2[i] - c1[i]) * t
    return Image.fromarray(arr.astype(np.uint8), "RGB")

def background(size):
    w, h = size
    bg = gradient(size, (11, 13, 20), (27, 31, 74), 120).convert("RGBA")
    glow = Image.new("RGBA", size, (0, 0, 0, 0)); d = ImageDraw.Draw(glow)
    d.ellipse([-w * 0.2, -h * 0.25, w * 0.75, h * 0.6], fill=BLUE + (70,))
    d.ellipse([w * 0.35, h * 0.45, w * 1.2, h * 1.25], fill=VIOLET + (60,))
    glow = glow.filter(ImageFilter.GaussianBlur(w * 0.12))
    return Image.alpha_composite(bg, glow)

def mark(px, scale=1.0, ss=4):
    """Ring + W on a transparent square canvas of px pixels. scale 1.0 == the adaptive-icon artwork (66/108 safe zone)."""
    S = px * ss
    unit = S / 108.0 * scale
    c = S / 2.0
    def P(x, y): return (c + (x - 54) * unit, c + (y - 54) * unit)
    ring = Image.new("L", (S, S), 0); dr = ImageDraw.Draw(ring)
    r = 30 * unit; lw = max(2, int(2.6 * unit))
    dr.ellipse([c - r, c - r, c + r, c + r], outline=255, width=lw)
    wm = Image.new("L", (S, S), 0); dw = ImageDraw.Draw(wm)
    pts = [P(36, 40), P(45, 69), P(54, 50), P(63, 69), P(72, 40)]
    sw = int(5.2 * unit)
    dw.line(pts, fill=255, width=sw, joint="curve")
    for x, y in pts: dw.ellipse([x - sw / 2, y - sw / 2, x + sw / 2, y + sw / 2], fill=255)
    fill = gradient((S, S), BLUE, VIOLET, 45).convert("RGBA")
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    halo = Image.new("RGBA", (S, S), BLUE + (0,)); halo.putalpha(wm.filter(ImageFilter.GaussianBlur(unit * 2.2)).point(lambda v: int(v * 0.55)))
    out = Image.alpha_composite(out, halo)
    for m in (ring, wm):
        layer = fill.copy(); layer.putalpha(m); out = Image.alpha_composite(out, layer)
    return out.resize((px, px), Image.LANCZOS)

def icon(px, scale=1.35):
    bg = background((px, px))
    return Image.alpha_composite(bg, mark(px, scale)).convert("RGBA")

def font(path, size):
    return ImageFont.truetype(path, size)

def feature_graphic(w=1024, h=500):
    bg = background((w, h))
    # larger glow band
    m = mark(380, 1.0)
    bg.alpha_composite(m, (70, (h - 380) // 2))
    d = ImageDraw.Draw(bg)
    d.text((430, 150), "WYRIX", font=font(FONT_BOLD, 96), fill=(240, 244, 255))
    d.text((434, 262), "BROWSER", font=font(FONT_BOLD, 44), fill=BLUE + (255,))
    d.text((434, 335), "Fast. Private. Yours.", font=font(FONT, 34), fill=(180, 188, 214))
    return bg.convert("RGB")

def save(img, *path):
    p = os.path.join(OUT, *path); os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p, optimize=True); print("wrote", os.path.normpath(p), img.size, os.path.getsize(p) // 1024, "KB")

if __name__ == "__main__":
    save(icon(512), "play", "icon-512.png")                 # Google Play hi-res icon (full square; Play applies the corner mask)
    save(feature_graphic(), "play", "feature-graphic-1024x500.png")
    save(icon(114), "amazon", "icon-114.png")               # Amazon Appstore small icon
    save(icon(512), "amazon", "icon-512.png")               # Amazon Appstore large icon
    save(feature_graphic(), "amazon", "promo-1024x500.png")
    # round + squircle preview for a quick visual check
    big = icon(512); mask = Image.new("L", (512, 512), 0); ImageDraw.Draw(mask).rounded_rectangle([0, 0, 511, 511], 112, fill=255)
    prev = Image.new("RGBA", (512, 512), (0, 0, 0, 0)); prev.paste(big, (0, 0), mask)
    save(prev, "preview-rounded.png")
