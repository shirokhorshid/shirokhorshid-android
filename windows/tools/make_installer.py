#!/usr/bin/env python3
"""Append the application files to the installer executable.

Layout of the resulting file:

    [installer.exe]
    [payload]
        "SHIRPAY1"
        u32  entry count
        per entry: u32 name_len, name (UTF-8), u32 data_len, data
    [trailer, last 16 bytes]
        u64  payload offset
        "SHIREND1"

Usage:
    python3 make_installer.py <installer.exe> <staging_dir> <output.exe>
                              [--placeholder <name>]
"""
import os
import struct
import sys

MAGIC = b"SHIRPAY1"
TRAILER = b"SHIREND1"


def build_payload(staging, placeholder_name):
    parts = [MAGIC]
    entries = []
    for root, _dirs, files in os.walk(staging):
        for name in sorted(files):
            full = os.path.join(root, name)
            rel = os.path.relpath(full, staging).replace("\\", "/")
            entries.append((rel, full))
    entries.sort()
    parts.append(struct.pack("<I", len(entries)))
    for rel, full in entries:
        name = rel.encode("utf-8")
        with open(full, "rb") as f:
            data = f.read()
        parts.append(struct.pack("<I", len(name)))
        parts.append(name)
        parts.append(struct.pack("<I", len(data)))
        parts.append(data)
        print("  + %-28s %8d bytes" % (rel, len(data)))
    return b"".join(parts), len(entries)


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        return 1
    installer, staging, out = sys.argv[1], sys.argv[2], sys.argv[3]

    with open(installer, "rb") as f:
        base = f.read()

    payload, count = build_payload(staging, "uninstall.exe")
    offset = len(base)
    with open(out, "wb") as f:
        f.write(base)
        f.write(payload)
        f.write(struct.pack("<Q", offset))
        f.write(TRAILER)

    print("packed %d files into %s (%d bytes total)"
          % (count, out, os.path.getsize(out)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
