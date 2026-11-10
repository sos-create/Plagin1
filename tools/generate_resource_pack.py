#!/usr/bin/env python3
"""Build the bundled Astral Nexus RPG 1.21.4 resource pack (stdlib only)."""
from __future__ import annotations
import json
import math
import random
import struct
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src" / "main" / "resources" / "resource-pack.zip"
random.seed(17114)

PALETTE = {
    "void": (5, 8, 20, 255), "deep": (8, 14, 30, 255),
    "blue": (14, 27, 49, 255), "blue2": (20, 37, 62, 255),
    "purple": (65, 43, 91, 255), "violet": (126, 83, 161, 255),
    "lilac": (184, 130, 215, 255), "pale": (230, 197, 245, 255),
    "gold": (200, 145, 60, 255), "gold2": (241, 197, 105, 255),
    "goldlight": (255, 225, 151, 255), "steel": (66, 79, 105, 255),
    "skin": (218, 151, 104, 255), "skinshadow": (150, 91, 68, 255),
    "hair": (72, 43, 35, 255), "coat": (17, 24, 42, 255),
    "coatlight": (43, 54, 78, 255), "pants": (37, 39, 56, 255),
    "white": (239, 238, 248, 255), "transparent": (0, 0, 0, 0),
}
W, H = 176, 222

class Canvas:
    def __init__(self, width: int, height: int, color):
        self.w, self.h = width, height
        self.px = [color] * (width * height)

    def put(self, x, y, color):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = color

    def rect(self, x, y, w, h, color):
        for yy in range(max(0, int(y)), min(self.h, int(y + h))):
            start = yy * self.w
            for xx in range(max(0, int(x)), min(self.w, int(x + w))):
                self.px[start + xx] = color

    def outline(self, x, y, w, h, color, thick=1):
        self.rect(x, y, w, thick, color)
        self.rect(x, y + h - thick, w, thick, color)
        self.rect(x, y, thick, h, color)
        self.rect(x + w - thick, y, thick, h, color)

    def line(self, x0, y0, x1, y1, color, thick=1):
        x0, y0, x1, y1 = map(int, (x0, y0, x1, y1))
        dx, sx = abs(x1 - x0), 1 if x0 < x1 else -1
        dy, sy = -abs(y1 - y0), 1 if y0 < y1 else -1
        err = dx + dy
        while True:
            self.rect(x0 - thick // 2, y0 - thick // 2, thick, thick, color)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def circle(self, cx, cy, r, color, fill=None):
        cx, cy, r = int(cx), int(cy), int(r)
        for y in range(-r, r + 1):
            for x in range(-r, r + 1):
                d = x * x + y * y
                if fill is not None and d <= r * r:
                    self.put(cx + x, cy + y, fill)
                elif abs(d - r * r) <= max(1, r):
                    self.put(cx + x, cy + y, color)

    def polygon(self, points, color):
        pts = [(int(x), int(y)) for x, y in points]
        min_y = max(0, min(y for _, y in pts))
        max_y = min(self.h - 1, max(y for _, y in pts))
        for y in range(min_y, max_y + 1):
            xs = []
            for i in range(len(pts)):
                x1, y1 = pts[i]
                x2, y2 = pts[(i + 1) % len(pts)]
                if y1 == y2:
                    continue
                if min(y1, y2) <= y < max(y1, y2):
                    xs.append(int(x1 + (y - y1) * (x2 - x1) / (y2 - y1)))
            xs.sort()
            for i in range(0, len(xs) - 1, 2):
                self.line(xs[i], y, xs[i + 1], y, color)

    def png(self):
        raw = bytearray()
        for y in range(self.h):
            raw.append(0)
            for x in range(self.w):
                raw.extend(self.px[y * self.w + x])
        def chunk(kind: bytes, data: bytes):
            body = kind + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xffffffff)
        header = struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0)
        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
                + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))

def background(seed: int, brighter: bool = False) -> Canvas:
    rnd = random.Random(seed)
    c = Canvas(W, H, PALETTE["void"])
    for y in range(H):
        for x in range(W):
            glow = max(0.0, 1.0 - math.sqrt(((x - 87) / 112) ** 2 + ((y - 90) / 150) ** 2))
            side = max(0.0, 1.0 - abs(x - 10) / 35) * 0.15
            base = (5 + int(8 * glow), 8 + int(13 * glow), 20 + int(25 * glow))
            if brighter:
                base = (base[0] + 2, base[1] + 2, base[2] + 5)
            c.put(x, y, (min(255, base[0] + int(4 * side)),
                         min(255, base[1] + int(5 * side)),
                         min(255, base[2] + int(10 * side)), 255))
    # star field with tiny four-point glints
    for i in range(185):
        x, y = rnd.randrange(W), rnd.randrange(H)
        col = rnd.choice([PALETTE["steel"], PALETTE["violet"], PALETTE["lilac"], PALETTE["gold"]])
        c.put(x, y, col)
        if i % 17 == 0:
            c.line(x - 2, y, x + 2, y, col)
            c.line(x, y - 2, x, y + 2, col)
    # engraved double border and corners
    c.outline(1, 1, 174, 220, (67, 51, 96, 255), 2)
    c.outline(4, 4, 168, 214, PALETTE["lilac"], 1)
    c.outline(6, 6, 164, 210, (40, 34, 68, 255), 1)
    for x, y in [(9, 9), (166, 9), (9, 212), (166, 212)]:
        c.circle(x, y, 4, PALETTE["gold2"])
        c.circle(x, y, 2, PALETTE["violet"])
    return c

def equipment_art():
    c = background(71)
    # violet aura around the center and blue nebula wisps
    for cx, cy, r, col in [(88, 65, 51, (26, 28, 57, 255)),
                           (88, 65, 44, (20, 25, 49, 255)),
                           (20, 72, 26, (17, 30, 55, 255)),
                           (153, 76, 25, (26, 25, 52, 255))]:
        c.circle(cx, cy, r, col, fill=col)
    # glowing left edge, like a magical energy seam
    c.line(8, 13, 8, 207, PALETTE["lilac"], 2)
    c.line(10, 13, 10, 207, (78, 57, 110, 255), 1)
    # header rail and side armour bays
    c.line(12, 16, 164, 16, PALETTE["gold2"])
    c.line(12, 18, 164, 18, (67, 49, 83, 255))
    for y in (35, 53, 71, 89):
        c.rect(12, y, 45, 17, (7, 13, 28, 255))
        c.outline(12, y, 45, 17, (89, 66, 119, 255))
        c.line(14, y + 1, 55, y + 1, PALETTE["lilac"])
        c.rect(119, y, 45, 17, (7, 13, 28, 255))
        c.outline(119, y, 45, 17, (119, 86, 49, 255))
        c.line(121, y + 1, 162, y + 1, PALETTE["gold2"])
    # central astral ring and segmented runes
    cx, cy = 88, 67
    for r, col in [(42, (61, 46, 86, 255)), (38, PALETTE["gold2"]),
                   (35, PALETTE["violet"]), (31, (46, 39, 72, 255))]:
        c.circle(cx, cy, r, col)
    for angle in range(0, 360, 15):
        a = math.radians(angle)
        r1 = 39 if angle % 45 == 0 else 41
        r2 = 45 if angle % 45 == 0 else 43
        col = PALETTE["gold2"] if angle % 45 == 0 else (99, 74, 125, 255)
        c.line(cx + math.cos(a) * r1, cy + math.sin(a) * r1,
               cx + math.cos(a) * r2, cy + math.sin(a) * r2, col)
    # Minecraft-style adventurer silhouette aligned with the centre portrait slot (slot 22).
    c.circle(88, 63, 14, (35, 24, 31, 255), fill=(218, 150, 104, 255))
    c.rect(76, 51, 24, 9, PALETTE["hair"])
    c.rect(75, 56, 5, 9, PALETTE["hair"])
    c.rect(97, 57, 4, 7, PALETTE["hair"])
    c.rect(81, 63, 4, 4, (42, 65, 112, 255))
    c.rect(92, 63, 4, 4, (42, 65, 112, 255))
    c.rect(84, 72, 9, 6, PALETTE["skinshadow"])
    c.polygon([(75, 77), (82, 74), (94, 74), (102, 78),
               (107, 104), (69, 104)], PALETTE["coat"])
    c.rect(83, 77, 10, 26, PALETTE["coatlight"])
    c.line(88, 77, 88, 102, PALETTE["gold"])
    c.rect(67, 80, 7, 24, PALETTE["coat"])
    c.rect(102, 80, 7, 24, PALETTE["coat"])
    c.polygon([(75, 104), (87, 104), (85, 132), (77, 132)], PALETTE["pants"])
    c.polygon([(90, 104), (101, 104), (100, 132), (92, 132)], PALETTE["pants"])
    c.rect(75, 131, 12, 5, (128, 86, 61, 255))
    c.rect(91, 131, 11, 5, (128, 86, 61, 255))
    c.line(76, 105, 101, 105, PALETTE["gold2"], 2)
    # small side medallions and gem details
    for x, y in [(35, 43), (35, 61), (35, 79), (35, 97),
                 (141, 43), (141, 61), (141, 79), (141, 97)]:
        c.circle(x, y, 5, PALETTE["gold"])
        c.circle(x, y, 3, PALETTE["violet"])
        c.put(x, y, PALETTE["goldlight"])
    # These two plates line up with the live RPG info slots 31 and 40.
    c.rect(47, 72, 82, 18, (7, 12, 26, 255))
    c.outline(47, 72, 82, 18, PALETTE["violet"])
    c.line(50, 73, 126, 73, PALETTE["gold2"])
    c.rect(47, 90, 82, 18, (7, 12, 26, 255))
    c.outline(47, 90, 82, 18, PALETTE["violet"])
    c.line(50, 91, 126, 91, PALETTE["gold2"])
    c.circle(88, 99, 5, PALETTE["gold2"])
    # The lower player inventory progress rail and hotbar trim.
    c.rect(14, 164, 148, 7, (6, 10, 22, 255))
    c.outline(14, 164, 148, 7, (101, 75, 131, 255))
    c.line(16, 166, 84, 166, PALETTE["lilac"], 2)
    c.rect(7, 180, 162, 25, (6, 10, 22, 255))
    c.outline(7, 180, 162, 25, (128, 92, 160, 255))
    for i in range(9):
        x = 9 + i * 18
        edge = PALETTE["gold2"] if i == 4 else (110, 80, 139, 255)
        c.outline(x, 182, 16, 17, edge)
        c.line(x + 1, 183, x + 14, 183, (48, 42, 69, 255))
    c.line(9, 213, 167, 213, PALETTE["gold"])
    return c.png()

def skills_art():
    c = background(909, True)
    # A constellation whose four main nodes sit exactly behind slots 10, 16, 28 and 34.
    nodes = {
        "core": (88, 63),
        "power": (35, 45), "wisdom": (141, 45),
        "vital": (35, 81), "focus": (141, 81),
        "upper": (88, 23), "lower": (88, 117),
        "ul": (15, 24), "ur": (161, 24),
        "ll": (15, 117), "lr": (161, 117),
        "upper_left": (59, 27), "upper_right": (117, 27),
        "lower_left": (59, 112), "lower_right": (117, 112),
        "left_mid": (15, 63), "right_mid": (161, 63),
        "left_inner": (59, 63), "right_inner": (117, 63),
    }
    edges = [
        ("core", "power"), ("core", "wisdom"),
        ("core", "vital"), ("core", "focus"),
        ("power", "upper"), ("wisdom", "upper"),
        ("vital", "lower"), ("focus", "lower"),
        ("ul", "upper_left"), ("upper_left", "upper"),
        ("upper", "upper_right"), ("upper_right", "ur"),
        ("ll", "lower_left"), ("lower_left", "lower"),
        ("lower", "lower_right"), ("lower_right", "lr"),
        ("left_mid", "power"), ("right_mid", "wisdom"),
        ("power", "left_inner"), ("wisdom", "right_inner"),
        ("left_inner", "vital"), ("right_inner", "focus"),
    ]
    for a, b in edges:
        x1, y1 = nodes[a]; x2, y2 = nodes[b]
        c.line(x1, y1, x2, y2, (103, 70, 38, 255), 3)
        c.line(x1, y1 - 1, x2, y2 - 1, PALETTE["gold2"], 1)
    for name, (x, y) in nodes.items():
        r = 10 if name == "core" else (7 if name in
             ("power", "wisdom", "vital", "focus", "upper", "lower") else 4)
        c.circle(x, y, r, PALETTE["gold2"], fill=(10, 14, 29, 255))
        c.circle(x, y, max(2, r - 3), PALETTE["violet"])
        c.put(x, y, PALETTE["goldlight"])
        if r >= 7:
            c.line(x - r // 2, y, x + r // 2, y, PALETTE["gold"])
            c.line(x, y - r // 2, x, y + r // 2, PALETTE["gold"])
    # Skill point counter sits behind slot 40, not over the constellation.
    c.rect(47, 90, 82, 18, (6, 10, 23, 255))
    c.outline(47, 90, 82, 18, (107, 78, 139, 255))
    c.line(50, 91, 126, 91, PALETTE["gold2"])
    c.circle(88, 99, 5, PALETTE["gold2"])
    # Separate lower panel remains visible behind the player's own inventory.
    c.rect(9, 171, 158, 32, (6, 10, 23, 255))
    c.outline(9, 171, 158, 32, (107, 78, 139, 255))
    c.line(13, 173, 163, 173, PALETTE["gold2"])
    c.rect(18, 187, 140, 7, (18, 17, 35, 255))
    c.outline(18, 187, 140, 7, (116, 82, 143, 255))
    c.line(20, 189, 78, 189, PALETTE["lilac"], 2)
    c.circle(88, 190, 8, PALETTE["gold2"])
    c.circle(88, 190, 5, PALETTE["violet"])
    return c.png()

def icon_png(kind):
    c = Canvas(32, 32, PALETTE["transparent"])
    if kind == "shield":
        c.polygon([(16, 2), (27, 6), (26, 19), (16, 30), (6, 19), (5, 6)],
                  (60, 43, 96, 255))
        c.polygon([(16, 4), (24, 7), (23, 18), (16, 26), (9, 18), (8, 7)],
                  (34, 95, 112, 255))
        c.line(16, 5, 16, 25, PALETTE["gold2"], 2)
        c.line(9, 11, 23, 11, (115, 204, 204, 255), 2)
        c.circle(16, 15, 3, PALETTE["goldlight"], fill=PALETTE["gold"])
    elif kind == "sword":
        c.line(8, 25, 24, 7, (180, 201, 224, 255), 4)
        c.line(10, 23, 25, 6, PALETTE["white"], 2)
        c.line(6, 22, 13, 28, PALETTE["gold2"], 3)
        c.line(8, 26, 13, 21, PALETTE["gold"], 2)
        c.circle(6, 27, 2, PALETTE["goldlight"], fill=PALETTE["gold"])
    elif kind == "nexus":
        c.circle(16, 16, 13, PALETTE["violet"])
        c.circle(16, 16, 10, PALETTE["gold2"])
        c.polygon([(16, 3), (19, 12), (28, 16), (19, 19),
                   (16, 29), (13, 19), (4, 16), (13, 12)],
                  PALETTE["goldlight"])
        c.circle(16, 16, 4, (210, 245, 255, 255), fill=(210, 245, 255, 255))
    else:  # mana
        c.circle(16, 16, 13, (58, 38, 102, 255))
        c.circle(16, 16, 10, (92, 54, 168, 255))
        c.polygon([(16, 4), (23, 15), (16, 27), (9, 15)], (114, 209, 246, 255))
        c.line(12, 14, 16, 8, (225, 248, 255, 255), 2)
        c.circle(16, 16, 3, PALETTE["lilac"], fill=PALETTE["lilac"])
    return c.png()

def main():
    assets = {
        "pack.mcmeta": json.dumps({"pack": {"pack_format": 46,
            "description": "Astral Nexus RPG - custom fantasy GUI textures"}}, ensure_ascii=False).encode(),
        "pack.png": icon_png("nexus"),
        "assets/astral/font/gui.json": json.dumps({"providers": [
            {"type": "bitmap", "file": "astral:font/equipment.png",
             "ascent": 6, "height": 222, "chars": ["\ue000"]},
            {"type": "bitmap", "file": "astral:font/skills.png",
             "ascent": 6, "height": 222, "chars": ["\ue001"]},
            {"type": "bitmap", "file": "astral:font/submenu.png",
             "ascent": 6, "height": 222, "chars": ["\ue002"]}
        ]}, ensure_ascii=False).encode(),
        "assets/astral/textures/font/equipment.png": equipment_art(),
        "assets/astral/textures/font/skills.png": skills_art(),
        "assets/astral/textures/font/submenu.png": background(125, True).png(),
        "assets/astral/textures/item/nexus.png": icon_png("nexus"),
        "assets/astral/textures/item/sword.png": icon_png("sword"),
        "assets/astral/textures/item/mana.png": icon_png("mana"),
        "assets/astral/textures/item/shield.png": icon_png("shield"),
        "assets/astral/models/item/nexus.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/nexus"}}',
        "assets/astral/models/item/sword.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/sword"}}',
        "assets/astral/models/item/mana.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/mana"}}',
        "assets/minecraft/models/item/nether_star.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/nether_star"},"overrides":[{"predicate":{"custom_model_data":1001},"model":"astral:item/nexus"}]}',
        "assets/minecraft/models/item/iron_sword.json": b'{"parent":"minecraft:item/handheld","textures":{"layer0":"minecraft:item/iron_sword"},"overrides":[{"predicate":{"custom_model_data":1002},"model":"astral:item/sword"}]}',
        "assets/minecraft/models/item/lapis_lazuli.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/lapis_lazuli"},"overrides":[{"predicate":{"custom_model_data":1003},"model":"astral:item/mana"}]}',
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as pack:
        for name, data in assets.items():
            pack.writestr(name, data)
    # A second copy in build/libs is uploaded as a separate, manually installable artifact.
    libs = ROOT / "build" / "libs"
    libs.mkdir(parents=True, exist_ok=True)
    version = "1.12.0"
    (libs / f"AstralNexus-ResourcePack-{version}.zip").write_bytes(OUT.read_bytes())
    print(f"Generated {OUT} ({OUT.stat().st_size} bytes, {len(assets)} files)")
    print(f"Standalone pack: {libs / f'AstralNexus-ResourcePack-{version}.zip'}")

if __name__ == "__main__":
    main()
