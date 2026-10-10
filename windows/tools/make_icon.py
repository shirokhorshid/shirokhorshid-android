#!/usr/bin/env python3
"""Build a multi-resolution Windows .ico from the app launcher PNG.

Pure Python (no PIL): decodes the source PNG, box-downsamples it, then writes
PNG-compressed ICO entries (supported by Windows Vista and later).

Usage:
    python3 make_icon.py <src.png> <out.ico> [sizes...]
"""
import os
import struct
import sys
import zlib


# ---------------------------------------------------------------- PNG decode

def _chunks(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos = 8
    while pos + 8 <= len(data):
        (length,) = struct.unpack_from(">I", data, pos)
        ctype = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        yield ctype, body
        pos += 12 + length


def decode_png(path):
    """Return (width, height, list of rows as bytearray of RGBA8)."""
    data = open(path, "rb").read()
    idat = b""
    w = h = depth = ctype = None
    palette = None
    trns = None
    for ctype_name, body in _chunks(data):
        if ctype_name == b"IHDR":
            w, h, depth, ctype, comp, filt, inter = struct.unpack(">IIBBBBB", body)
            if inter:
                raise ValueError("interlaced PNG unsupported")
            if ctype not in (0, 2, 3, 4, 6):
                raise ValueError("unsupported colour type %d" % ctype)
        elif ctype_name == b"PLTE":
            palette = body
        elif ctype_name == b"tRNS":
            trns = body
        elif ctype_name == b"IDAT":
            idat += body
        elif ctype_name == b"IEND":
            break
    raw = zlib.decompress(idat)

    if ctype == 3:
        channels, bps = 1, 8
    elif ctype in (0, 4):
        channels, bps = 1, depth
    elif ctype in (2, 6):
        channels, bps = depth // 8 * (4 if ctype == 6 else 3), 8
    else:
        raise ValueError("bad colour type")

    bpp = max(1, channels)          # bytes per pixel
    stride = (w * channels * (bps // 8 if ctype != 3 else 1))
    if ctype != 3:
        stride = (w * bpp)

    out = bytearray(h * stride)
    prev = bytearray(stride)
    pos = 0
    for y in range(h):
        ft = raw[pos]
        pos += 1
        line = bytearray(raw[pos:pos + stride])
        pos += stride
        if ft == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif ft == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ft == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ft == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        elif ft != 0:
            raise ValueError("bad filter %d" % ft)
        out[y * stride:(y + 1) * stride] = line
        prev = line

    # Normalise to RGBA8 rows
    rows = []
    if ctype == 6:
        if bps == 2:      # 16-bit RGBA -> take high byte
            for y in range(h):
                r = out[y * stride:(y + 1) * stride]
                rows.append(bytearray(r[i] for i in range(1, stride, 2)))
        else:
            for y in range(h):
                rows.append(out[y * stride:(y + 1) * stride])
    elif ctype == 2:
        if bps == 2:
            for y in range(h):
                r = out[y * stride:(y + 1) * stride]
                rows.append(bytearray(r[i] for i in range(1, stride, 2)))
        else:
            for y in range(h):
                rows.append(out[y * stride:(y + 1) * stride])
        rows = [bytearray().join(
            bytearray((r[i * 3], r[i * 3 + 1], r[i * 3 + 2], 255))
            for i in range(w)) for r in rows]
    elif ctype == 3:
        for y in range(h):
            r = out[y * stride:(y + 1) * stride]
            row = bytearray()
            for i in range(w):
                idx = r[i] * 3
                row += bytes(palette[idx:idx + 3])
                row += bytes((255,))
            rows.append(row)
    elif ctype == 0:
        for y in range(h):
            r = out[y * stride:(y + 1) * stride]
            row = bytearray()
            for i in range(w):
                v = r[i * (bps // 8)] if bps == 16 else r[i]
                row += bytes((v, v, v, 255))
            rows.append(row)
    elif ctype == 4:
        for y in range(h):
            r = out[y * stride:(y + 1) * stride]
            row = bytearray()
            for i in range(w):
                v = r[i * (bps // 8)] if bps == 16 else r[i]
                row += bytes((v, v, v, 255))
            rows.append(row)
    return w, h, rows


# ---------------------------------------------------------------- PNG encode

def encode_png(w, h, rows):
    raw = bytearray()
    for r in rows:
        raw.append(0)          # filter type 0 (None) for every scanline
        raw += r

    def chunk(tag, body):
        return (struct.pack(">I", len(body)) + tag + body
                + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def resize(w, h, rows, size):
    """Premultiplied-alpha box downsample to size x size."""
    if w == size and h == size:
        return [bytearray(r) for r in rows]
    out = []
    x_ratio = w / float(size)
    y_ratio = h / float(size)
    for y in range(size):
        y0 = int(y * y_ratio)
        y1 = max(y0 + 1, int((y + 1) * y_ratio))
        row = bytearray()
        for x in range(size):
            x0 = int(x * x_ratio)
            x1 = max(x0 + 1, int((x + 1) * x_ratio))
            ar = ag = ab = aa = n = 0
            for yy in range(y0, min(y1, h)):
                src = rows[yy]
                for xx in range(x0, min(x1, w)):
                    o = xx * 4
                    a = src[o + 3]
                    ar += src[o] * a
                    ag += src[o + 1] * a
                    ab += src[o + 2] * a
                    aa += a
                    n += 1
            if aa:
                a = aa // n
                row += bytes((ar // aa, ag // aa, ab // aa, a))
            else:
                row += bytes((0, 0, 0, 0))
        out.append(row)
    return out


# ---------------------------------------------------------------- ICO writer

def write_ico(path, images):
    """images: list of (size, png_bytes)."""
    n = len(images)
    header = struct.pack("<HHH", 0, 1, n)
    offset = 6 + 16 * n
    entries = b""
    blobs = b""
    for size, png in images:
        entries += struct.pack("<BBBBHHII",
                               size if size < 256 else 0,
                               size if size < 256 else 0,
                               0, 0, 1, 32, len(png), offset)
        blobs += png
        offset += len(png)
    with open(path, "wb") as f:
        f.write(header + entries + blobs)


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    src, dst = sys.argv[1], sys.argv[2]
    sizes = [int(s) for s in sys.argv[3:]] or [256, 128, 64, 48, 32, 16]
    w, h, rows = decode_png(src)
    images = []
    for s in sizes:
        r = resize(w, h, rows, s)
        images.append((s, encode_png(s, s, r)))
    os.makedirs(os.path.dirname(os.path.abspath(dst)), exist_ok=True)
    write_ico(dst, images)
    print("wrote %s (%d sizes: %s)" % (dst, len(images), ", ".join(map(str, sizes))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
