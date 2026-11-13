#!/usr/bin/env python3
"""Draws the boss's silhouette for the horizon (assets/thepale/textures/environment/abomination_silhouette.png).

A placeholder until there is art: python3 tools/gen_silhouette.py. Needs Pillow.
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

W, H, SCALE = 192, 128, 4
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "thepale",
                   "textures", "environment", "abomination_silhouette.png")


def tentacle(draw, points, width_from, width_to):
    """A tapering stroke along a smooth curve through the points (Catmull-Rom)."""
    chain = [points[0]] + list(points) + [points[-1]]
    steps = 120
    for i in range(steps + 1):
        t = i / steps
        f = t * (len(points) - 1)
        k = min(int(f), len(points) - 2)
        u = f - k
        p0, p1, p2, p3 = chain[k], chain[k + 1], chain[k + 2], chain[k + 3]
        x, y = (0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * u + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * u * u
                       + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * u ** 3) for j in (0, 1))
        r = (width_from + (width_to - width_from) * t ** 0.8) / 2
        draw.ellipse([x - r, y - r, x + r, y + r], fill=255)


def main():
    random.seed(7)
    big = (W * SCALE, H * SCALE)
    mask = Image.new("L", big, 0)
    draw = ImageDraw.Draw(mask)
    cx, cy, r = W * SCALE / 2, H * SCALE * 0.36, H * SCALE * 0.27
    draw.ellipse([cx - r * 1.08, cy - r, cx + r * 1.08, cy + r], fill=255)
    ground = H * SCALE * 0.97
    # Tentacles: out from the lower half, arching, then curling down to the water.
    for side, curl in [(-1.0, -1), (-0.62, 1), (-0.3, -1), (0.28, 1), (0.6, -1), (1.0, 1)]:
        reach = side * W * SCALE * 0.4
        start = (cx + side * r * 0.75, cy + r * (0.7 - 0.35 * abs(side)))
        knee = (cx + reach * 0.8, cy + r * (0.55 + 0.5 * (1 - abs(side))))
        mid = (cx + reach * 1.08 + curl * r * 0.12, cy + r * 1.45)
        low = (cx + reach * 0.95 - curl * r * 0.1, cy + r * 1.95)
        end = (cx + reach * 1.0 + curl * r * 0.25, ground)
        tentacle(draw, [start, knee, mid, low, end], r * 0.5, r * 0.2)
    # The two with the face and the hand, raised beside the body.
    tentacle(draw, [(cx - r * 0.7, cy - r * 0.4), (cx - r * 1.45, cy - r * 1.0), (cx - r * 2.05, cy - r * 0.75), (cx - r * 2.2, cy - r * 0.1)], r * 0.4, r * 0.3)
    tentacle(draw, [(cx + r * 0.7, cy - r * 0.4), (cx + r * 1.5, cy - r * 0.95), (cx + r * 2.1, cy - r * 0.6), (cx + r * 2.15, cy + r * 0.1)], r * 0.4, r * 0.22)
    mask = mask.filter(ImageFilter.GaussianBlur(SCALE * 0.6))

    image = Image.new("RGBA", big, (0, 0, 0, 0))
    px = image.load()
    mp = mask.load()
    for y in range(big[1]):
        # Fades into the mist toward the water.
        mist = 1.0 if y < ground * 0.72 else max(0.0, 1.0 - (y - ground * 0.72) / (ground * 0.28)) ** 0.8
        for x in range(big[0]):
            a = mp[x, y]
            if a:
                shade = 1.0 - 0.35 * (y / big[1])
                px[x, y] = (int(30 * shade), int(20 * shade), int(42 * shade), int(a * (0.25 + 0.75 * mist)))
    draw = ImageDraw.Draw(image)
    glow = (236, 226, 255, 255)
    # Eye slits and the one open eye.
    for ex, ey, ew, tilt in [(-0.45, -0.25, 0.42, -0.25), (0.05, 0.2, 0.5, 0.12), (-0.5, 0.35, 0.3, 0.2)]:
        x0, y0 = cx + ex * r, cy + ey * r
        half = ew * r / 2
        dy = math.tan(tilt) * half
        draw.polygon([(x0 - half, y0 - dy), (x0, y0 - r * 0.045), (x0 + half, y0 + dy), (x0, y0 + r * 0.045)], fill=glow)
    ex, ey, er = cx + r * 0.52, cy - r * 0.18, r * 0.2
    draw.ellipse([ex - er, ey - er * 0.72, ex + er, ey + er * 0.72], fill=glow)
    draw.ellipse([ex - er * 0.4, ey - er * 0.4, ex + er * 0.4, ey + er * 0.4], fill=(24, 14, 34, 255))
    image = image.resize((W, H), Image.LANCZOS)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
