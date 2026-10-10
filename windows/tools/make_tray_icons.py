#!/usr/bin/env python3
"""Generate the tray icons (off = grey, on = green) used by the Windows app.

Draws a small "sun" emblem (the Khorshid) on a rounded disc, so the tray badge
matches the app identity at 16x16.

Usage:
    python3 make_tray_icons.py <out_dir>
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_icon import encode_png, write_ico  # noqa: E402


def lerp(a, b, t):
    return a + (b - a) * t


def draw(size, base_rgb):
    """Return RGBA rows (bytearray per scanline) for a size x size icon."""
    cx = cy = (size - 1) / 2.0
    disc = size / 2.0 - size * 0.03
    core = size * 0.17
    ray_in = size * 0.24
    ray_out = size * 0.34
    rows = []
    for y in range(size):
        row = bytearray()
        for x in range(size):
            fx, fy = x + 0.5, y + 0.5
            dx, dy = fx - cx, fy - cy
            dist = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            a = 0.0
            r, g, b = base_rgb

            # disc with 1px feathered edge
            if dist <= disc - 1.0:
                a = 1.0
            elif dist <= disc:
                a = max(0.0, min(1.0, disc - dist))

            if a > 0.0:
                # subtle top-light shading
                shade = 1.0 + 0.16 * (1.0 - (fy / size)) - 0.10 * (dist / disc)
                r = int(max(0, min(255, r * shade)))
                g = int(max(0, min(255, g * shade)))
                b = int(max(0, min(255, b * shade)))

                # sun core
                if dist <= core:
                    t = 1.0 - dist / max(core, 1e-6)
                    a2 = 1.0
                    r, g, b = int(lerp(r, 255, 0.95)), int(lerp(g, 250, 0.95)), int(lerp(b, 210, 0.95))
                    a = max(a, a2)
                # rays
                elif ray_in <= dist <= ray_out:
                    # 16 rays
                    k = ((ang + math.pi) / (2 * math.pi)) * 16.0
                    frac = abs(k - round(k))
                    if frac < 0.18:
                        bright = 1.0 - frac / 0.18
                        r = int(lerp(r, 255, bright))
                        g = int(lerp(g, 252, bright))
                        b = int(lerp(b, 224, bright))
            row += bytes((int(r), int(g), int(b), int(round(255 * a))))
        rows.append(row)
    return rows


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "res"
    os.makedirs(out, exist_ok=True)
    for name, colour in (("tray-off.ico", (0x75, 0x75, 0x75)),
                         ("tray-on.ico", (0x00, 0xA0, 0x50))):
        sizes = [32, 16]
        images = [(s, encode_png(s, s, draw(s, colour))) for s in sizes]
        write_ico(os.path.join(out, name), images)
        print("wrote %s" % os.path.join(out, name))


if __name__ == "__main__":
    sys.exit(main())
