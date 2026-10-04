#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generates the GTQT splash textures from the source logo.

Output (power-of-two, RGBA, straight alpha):

  src/main/resources/assets/quantumhue/textures/gui/title/gtqt_logo.png   1024x1024

That is the only texture the splash needs.  The "GTQT" and "格雷：量子跃迁" glyph strips that used
to live alongside it were removed along with the wordmark: both screens now show the emblem on its
own, and a texture nothing loads is a texture that can quietly go missing.

Usage:
    python tools/gen_gtqt_splash_assets.py
    python tools/gen_gtqt_splash_assets.py --source "C:/path/to/logo.png"
"""

import argparse
import os
import sys

import numpy as np
from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SOURCE = r"C:\Users\Meowmel\Desktop\素材库\gtqt\Image_1717502995875.png"
OUT_DIR = os.path.join(REPO, "src", "main", "resources", "assets", "quantumhue", "textures", "gui", "title")

LOGO_SIZE = 1024
LOGO_PADDING = 0.04  # fraction of the square canvas kept as breathing room


def log(msg):
    print(f"[assets] {msg}")


def alpha_bbox(img):
    """Bounding box of everything with alpha > threshold, using numpy (fast on 4000x4000)."""
    a = np.asarray(img.getchannel("A"))
    ys, xs = np.nonzero(a > 8)
    if len(xs) == 0:
        return (0, 0, img.width, img.height)
    return (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)


def resize_rgba_premultiplied(img, size):
    """
    Lanczos resize that is correct across the alpha edge.

    Pillow resizes RGBA channels independently, so fully transparent pixels (usually (0,0,0,0))
    leak black into the semi-transparent rim and produce a dark halo.  Premultiplying first and
    dividing afterwards removes that artefact.
    """
    src = np.asarray(img.convert("RGBA"), dtype=np.float32) / 255.0
    alpha = src[..., 3:4]
    premultiplied = np.concatenate([src[..., :3] * alpha, alpha], axis=2)

    pm8 = np.clip(premultiplied * 255.0 + 0.5, 0, 255).astype(np.uint8)
    small = np.asarray(Image.fromarray(pm8, "RGBA").resize(size, Image.LANCZOS), dtype=np.float32) / 255.0

    out_alpha = small[..., 3:4]
    safe = np.maximum(out_alpha, 1e-4)
    out_rgb = np.where(out_alpha > 1e-4, small[..., :3] / safe, 0.0)

    out = np.concatenate([np.clip(out_rgb, 0.0, 1.0), out_alpha], axis=2)
    return Image.fromarray(np.clip(out * 255.0 + 0.5, 0, 255).astype(np.uint8), "RGBA")


def build_logo(source_path):
    src = Image.open(source_path).convert("RGBA")
    log(f"source {os.path.basename(source_path)} {src.width}x{src.height}")

    box = alpha_bbox(src)
    cropped = src.crop(box)
    log(f"alpha bbox {box} -> content {cropped.width}x{cropped.height}")

    side = int(max(cropped.width, cropped.height) * (1.0 + LOGO_PADDING * 2))
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(cropped, ((side - cropped.width) // 2, (side - cropped.height) // 2))

    return resize_rgba_premultiplied(canvas, (LOGO_SIZE, LOGO_SIZE))


def assert_pot(img, name):
    def is_pot(v):
        return v > 0 and (v & (v - 1)) == 0

    if not (is_pot(img.width) and is_pot(img.height)):
        raise SystemExit(f"{name}: {img.width}x{img.height} is not power-of-two")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default=DEFAULT_SOURCE)
    args = parser.parse_args()

    if not os.path.isfile(args.source):
        raise SystemExit(f"source logo not found: {args.source}")

    os.makedirs(OUT_DIR, exist_ok=True)

    logo = build_logo(args.source)
    assert_pot(logo, "gtqt_logo.png")

    path = os.path.join(OUT_DIR, "gtqt_logo.png")
    logo.save(path, "PNG", optimize=True)
    log(f"wrote gtqt_logo.png  {logo.width}x{logo.height}  {os.path.getsize(path)} bytes")


if __name__ == "__main__":
    sys.exit(main())
