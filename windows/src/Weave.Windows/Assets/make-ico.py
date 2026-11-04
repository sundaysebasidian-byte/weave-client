#!/usr/bin/env python3
"""Build Assets/Weave.ico (16/20/24/32/40/48/64/256) from Assets/Weave.png.

Standard library only. Resampling is premultiplied-alpha area averaging, which is
what a 256 px source needs when it is reduced to 16-64 px; every entry is stored
as PNG, which Windows Vista and later read natively.

    python3 make-ico.py [source.png] [output.ico]
"""
import os
import struct
import sys
import zlib

SIZES = (16, 20, 24, 32, 40, 48, 64, 256)


def read_png(path):
    data = open(path, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit("not a PNG: " + path)
    pos, idat, header = 8, [], None
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            idat.append(body)
        elif kind == b"IEND":
            break
    width, height, depth, color, _, _, interlace = header
    if depth != 8 or color != 6 or interlace != 0:
        raise SystemExit("expected 8-bit non-interlaced RGBA PNG")
    raw = zlib.decompress(b"".join(idat))
    stride, bpp = width * 4, 4
    rows, previous = [], bytearray(stride)
    offset = 0
    for _ in range(height):
        filter_type = raw[offset]
        line = bytearray(raw[offset + 1:offset + 1 + stride])
        offset += 1 + stride
        for i in range(stride):
            left = line[i - bpp] if i >= bpp else 0
            up = previous[i]
            upper_left = previous[i - bpp] if i >= bpp else 0
            if filter_type == 1:
                line[i] = (line[i] + left) & 255
            elif filter_type == 2:
                line[i] = (line[i] + up) & 255
            elif filter_type == 3:
                line[i] = (line[i] + ((left + up) >> 1)) & 255
            elif filter_type == 4:
                p = left + up - upper_left
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - upper_left)
                predictor = left if pa <= pb and pa <= pc else up if pb <= pc else upper_left
                line[i] = (line[i] + predictor) & 255
        rows.append(line)
        previous = line
    return width, height, rows


def resample(width, height, rows, size):
    if size == width and size == height:
        return [bytes(row) for row in rows]
    scale = width / size
    out = []
    for y in range(size):
        y0, y1 = y * scale, (y + 1) * scale
        line = bytearray(size * 4)
        for x in range(size):
            x0, x1 = x * scale, (x + 1) * scale
            r = g = b = a = total = 0.0
            for sy in range(int(y0), min(height, int(y1) + 1)):
                wy = min(sy + 1, y1) - max(sy, y0)
                if wy <= 0:
                    continue
                row = rows[sy]
                for sx in range(int(x0), min(width, int(x1) + 1)):
                    wx = min(sx + 1, x1) - max(sx, x0)
                    if wx <= 0:
                        continue
                    weight = wx * wy
                    alpha = row[sx * 4 + 3] / 255.0
                    r += row[sx * 4] * alpha * weight
                    g += row[sx * 4 + 1] * alpha * weight
                    b += row[sx * 4 + 2] * alpha * weight
                    a += alpha * weight
                    total += weight
            if a > 0:
                line[x * 4:x * 4 + 4] = bytes((round(r / a), round(g / a), round(b / a), round(255 * a / total)))
        out.append(bytes(line))
    return out


def write_png(size, rows):
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + row for row in rows)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    source = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "Weave.png")
    target = sys.argv[2] if len(sys.argv) > 2 else os.path.join(here, "Weave.ico")
    width, height, rows = read_png(source)
    if width != height or width < max(SIZES):
        raise SystemExit("source must be a square PNG of at least %d px" % max(SIZES))
    images = [(size, write_png(size, resample(width, height, rows, size))) for size in SIZES]
    header = struct.pack("<HHH", 0, 1, len(images))
    offset = 6 + 16 * len(images)
    directory, payload = b"", b""
    for size, png in images:
        directory += struct.pack("<BBBBHHII", size % 256, size % 256, 0, 0, 1, 32, len(png), offset)
        payload += png
        offset += len(png)
    with open(target, "wb") as handle:
        handle.write(header + directory + payload)
    print("wrote %s (%d bytes, sizes %s)" % (target, offset, ", ".join(str(s) for s in SIZES)))


if __name__ == "__main__":
    main()
