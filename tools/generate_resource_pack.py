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
    for cx, cy, r, col in [(88, 55, 51, (26, 28, 57, 255)),
                           (88, 55, 44, (20, 25, 49, 255)),
                           (20, 72, 26, (17, 30, 55, 255)),
                           (153, 76, 25, (26, 25, 52, 255))]:
        c.circle(cx, cy, r, col, fill=col)
    # glowing left edge, like a magical energy seam
    c.line(8, 13, 8, 207, PALETTE["lilac"], 2)
    c.line(10, 13, 10, 207, (78, 57, 110, 255), 1)
    # Top navigation row occupies exactly GUI slots 0-8.
    for col in range(9):
        slot_frame(c, 17 + 18 * col, 27, PALETTE["gold2"] if col in (0,4,8) else PALETTE["lilac"])
    # Header rail and six real equipment sockets.
    c.line(12, 16, 164, 16, PALETTE["gold2"])
    c.line(12, 18, 164, 18, (67, 49, 83, 255))
    for y in (36, 54, 72, 90):
        c.rect(12, y, 45, 17, (7, 13, 28, 255))
        c.outline(12, y, 45, 17, (89, 66, 119, 255))
        c.line(14, y + 1, 55, y + 1, PALETTE["lilac"])
    for y in (36, 54):
        c.rect(119, y, 45, 17, (7, 13, 28, 255))
        c.outline(119, y, 45, 17, (119, 86, 49, 255))
        c.line(121, y + 1, 162, y + 1, PALETTE["gold2"])
    # Central portrait ring; player-head item is placed in slot 13 (x=88, y=45).
    cx, cy = 88, 55
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
    # Minecraft-style adventurer silhouette behind the live player head.
    c.circle(88, 45, 14, (35, 24, 31, 255), fill=(218, 150, 104, 255))
    c.rect(76, 33, 24, 9, PALETTE["hair"])
    c.rect(75, 38, 5, 9, PALETTE["hair"])
    c.rect(97, 39, 4, 7, PALETTE["hair"])
    c.rect(81, 45, 4, 4, (42, 65, 112, 255))
    c.rect(92, 45, 4, 4, (42, 65, 112, 255))
    c.rect(84, 54, 9, 6, PALETTE["skinshadow"])
    c.polygon([(75, 59), (82, 56), (94, 56), (102, 60),
               (107, 86), (69, 86)], PALETTE["coat"])
    c.rect(83, 59, 10, 25, PALETTE["coatlight"])
    c.line(88, 59, 88, 84, PALETTE["gold"])
    c.rect(67, 62, 7, 23, PALETTE["coat"])
    c.rect(102, 62, 7, 23, PALETTE["coat"])
    c.polygon([(75, 86), (87, 86), (85, 104), (77, 104)], PALETTE["pants"])
    c.polygon([(90, 86), (101, 86), (100, 104), (92, 104)], PALETTE["pants"])
    c.rect(75, 103, 12, 4, (128, 86, 61, 255))
    c.rect(91, 103, 11, 4, (128, 86, 61, 255))
    c.line(76, 87, 101, 87, PALETTE["gold2"], 2)
    # One level / experience socket at slot 49; no fake accessory placeholders.
    slot_frame(c, 89, 117, PALETTE["gold2"])
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
    for col in range(9):
        slot_frame(c, 17 + 18 * col, 27, PALETTE["gold2"] if col in (0,4,8) else PALETTE["lilac"])
    # A constellation whose four main nodes sit exactly behind slots 10, 16, 28 and 34.
    nodes = {
        # Main clickable talent slots: 10, 16, 28, 34.
        "core": (89, 63),
        "power": (35, 45), "wisdom": (143, 45),
        "vital": (35, 81), "focus": (143, 81),
        # Secondary clickable slots: 12, 14, 30, 32.
        "upper_left": (71, 45), "upper_right": (107, 45),
        "lower_left": (71, 81), "lower_right": (107, 81),
        "upper": (89, 23), "lower": (89, 117),
        "ul": (17, 24), "ur": (159, 24),
        "ll": (17, 117), "lr": (159, 117),
        "left_mid": (17, 63), "right_mid": (159, 63),
        "left_inner": (53, 63), "right_inner": (125, 63),
    }
    edges = [
        ("core", "power"), ("core", "wisdom"),
        ("core", "vital"), ("core", "focus"),
        ("power", "upper_left"), ("upper_left", "upper_right"),
        ("upper_right", "wisdom"),
        ("vital", "lower_left"), ("lower_left", "lower_right"),
        ("lower_right", "focus"),
        ("ul", "upper"), ("upper", "upper_left"),
        ("upper", "upper_right"), ("upper", "ur"),
        ("ll", "lower"), ("lower", "lower_left"),
        ("lower", "lower_right"), ("lower", "lr"),
        ("left_mid", "power"), ("left_mid", "vital"),
        ("right_mid", "wisdom"), ("right_mid", "focus"),
        ("power", "left_inner"), ("left_inner", "vital"),
        ("wisdom", "right_inner"), ("right_inner", "focus"),
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

def slot_frame(c: Canvas, x: int, y: int, accent=None):
    edge = accent or PALETTE["gold2"]
    c.rect(x - 8, y - 8, 16, 16, (7, 11, 25, 255))
    c.outline(x - 8, y - 8, 16, 16, (42, 35, 67, 255))
    c.line(x - 7, y - 7, x + 6, y - 7, edge)
    c.line(x - 7, y - 7, x - 7, y + 6, (107, 79, 139, 255))
    c.line(x + 7, y - 6, x + 7, y + 6, (25, 22, 43, 255))
    c.line(x - 6, y + 7, x + 6, y + 7, (25, 22, 43, 255))
    c.put(x - 6, y - 6, PALETTE["goldlight"])
    c.put(x + 6, y + 6, PALETTE["violet"])


def tab_art(kind: str):
    """Draw each GUI underlay with sockets exactly behind occupied inventory cells."""
    seeds = {"profile": 202, "class": 303, "quests": 404, "abilities": 505, "economy": 606, "shop": 707}
    c = background(seeds[kind], True)
    # Shared top navigation: slots 0-8, centres x=17+18*n, y=27.
    for col in range(9):
        slot_frame(c, 17 + 18 * col, 27, PALETTE["gold2"] if col in (0, 4, 8) else PALETTE["lilac"])

    if kind == "profile":
        for x, y in [(35,45),(71,45),(107,45),(143,45),
                     (35,81),(71,81),(107,81),(143,81)]:
            slot_frame(c, x, y, PALETTE["lilac"] if y == 45 else PALETTE["gold2"])
        c.circle(89, 133, 35, (28, 28, 54, 255))
        c.circle(89, 133, 29, (91, 65, 127, 255))
        c.circle(89, 133, 23, (17, 25, 48, 255))
        c.polygon([(89,108),(103,125),(89,143),(75,125)], (91, 122, 174, 255))
        c.polygon([(89,113),(98,125),(89,136),(80,125)], (173, 140, 221, 255))
    elif kind == "class":
        for x in (35,71,107,143):
            slot_frame(c, x, 45, PALETTE["gold2"])
        c.line(17, 81, 159, 81, (70, 51, 92, 255), 1)
        c.circle(89, 133, 20, (38, 34, 66, 255))
        c.circle(89, 133, 15, PALETTE["gold2"])
        c.polygon([(89,118),(99,133),(89,148),(79,133)], PALETTE["lilac"])
    elif kind == "quests":
        for x, y in [(35,45),(71,45),(107,45),(143,45),(35,81),(71,81)]:
            slot_frame(c, x, y, (164, 121, 79, 255))
        c.line(17, 110, 159, 110, (92, 66, 105, 255), 1)
        c.circle(89, 153, 22, (74, 52, 93, 255))
        c.circle(89, 153, 15, PALETTE["gold2"])
        c.polygon([(89,138),(94,149),(106,153),(94,157),(89,168),(84,157),(72,153),(84,149)], PALETTE["goldlight"])
    elif kind == "abilities":
        for x, y in [(35,45),(71,45),(107,45),(143,45),
                     (35,81),(71,81),(107,81),(143,81)]:
            slot_frame(c, x, y, PALETTE["violet"] if y == 81 else PALETTE["gold2"])
        c.circle(89, 146, 24, (38, 27, 64, 255))
        c.circle(89, 146, 17, PALETTE["gold2"])
        c.polygon([(89,128),(96,142),(110,146),(96,150),(89,164),(82,150),(68,146),(82,142)], PALETTE["goldlight"])
    elif kind == "economy":
        slot_frame(c, 89, 63, PALETTE["gold2"])
        c.circle(89, 132, 42, (46, 34, 69, 255))
        c.circle(89, 132, 34, PALETTE["gold2"])
        c.circle(89, 132, 27, (24, 31, 54, 255))
        c.polygon([(89,104),(108,132),(89,159),(70,132)], (178, 132, 69, 255))
        c.circle(89, 132, 9, PALETTE["goldlight"], fill=PALETTE["goldlight"])
    elif kind == "shop":
        for y in (45,63,81):
            for x in (35,71,107,143):
                slot_frame(c, x, y, PALETTE["gold2"] if y == 45 else PALETTE["lilac"])
        c.circle(89, 151, 25, (42, 31, 62, 255))
        c.circle(89, 151, 18, PALETTE["gold2"])
        c.rect(76, 140, 26, 21, (113, 73, 45, 255))
        c.outline(76, 140, 26, 21, PALETTE["goldlight"], 2)
        c.circle(89, 150, 4, PALETTE["lilac"], fill=PALETTE["lilac"])
    return c.png()


def icon_png(kind):
    c = Canvas(32, 32, PALETTE["transparent"])
    if kind in ("shield", "guard"):
        c.polygon([(16, 2), (27, 6), (26, 19), (16, 30), (6, 19), (5, 6)],
                  (42, 42, 76, 255))
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
    elif kind == "mana":
        c.circle(16, 16, 13, (58, 38, 102, 255))
        c.circle(16, 16, 10, (92, 54, 168, 255))
        c.polygon([(16, 4), (23, 15), (16, 27), (9, 15)], (114, 209, 246, 255))
        c.line(12, 14, 16, 8, (225, 248, 255, 255), 2)
        c.circle(16, 16, 3, PALETTE["lilac"], fill=PALETTE["lilac"])
    elif kind == "orb":
        c.circle(16, 16, 13, (68, 39, 115, 255))
        c.circle(16, 16, 10, (118, 74, 180, 255))
        c.circle(16, 16, 7, (76, 174, 227, 255), fill=(76, 174, 227, 255))
        c.line(10, 11, 15, 6, PALETTE["white"], 2)
        c.circle(22, 20, 2, PALETTE["goldlight"], fill=PALETTE["goldlight"])
    elif kind == "ring":
        c.circle(16, 17, 12, PALETTE["gold2"])
        c.circle(16, 17, 8, (0, 0, 0, 0))
        c.polygon([(16, 1), (22, 8), (16, 13), (10, 8)], PALETTE["lilac"])
        c.line(10, 20, 22, 20, (255, 229, 165, 255), 2)
    elif kind == "amulet":
        c.line(6, 4, 16, 17, PALETTE["gold2"], 2)
        c.line(26, 4, 16, 17, PALETTE["gold2"], 2)
        c.circle(16, 21, 8, PALETTE["gold2"])
        c.polygon([(16, 14), (22, 21), (16, 28), (10, 21)], (72, 160, 202, 255))
        c.circle(16, 21, 2, PALETTE["white"], fill=PALETTE["white"])
    elif kind == "belt":
        c.rect(3, 11, 26, 10, (126, 77, 39, 255))
        c.rect(5, 13, 22, 6, (188, 119, 54, 255))
        c.outline(12, 8, 9, 16, PALETTE["goldlight"], 2)
        c.rect(15, 13, 3, 6, PALETTE["gold2"])
    elif kind == "cloak":
        c.polygon([(9, 4), (23, 4), (28, 26), (16, 21), (4, 26)], (47, 37, 82, 255))
        c.line(10, 6, 16, 20, PALETTE["lilac"], 2)
        c.line(22, 6, 16, 20, PALETTE["violet"], 2)
        c.circle(16, 9, 3, PALETTE["gold2"], fill=PALETTE["gold"])
    elif kind == "rune":
        c.polygon([(16, 2), (27, 16), (16, 30), (5, 16)], (41, 73, 122, 255))
        c.polygon([(16, 5), (23, 16), (16, 27), (9, 16)], (79, 165, 224, 255))
        c.line(16, 7, 16, 25, PALETTE["white"], 2)
        c.line(10, 16, 22, 16, PALETTE["goldlight"], 2)
        c.line(11, 11, 21, 21, PALETTE["lilac"], 1)
    elif kind == "relic":
        c.rect(8, 3, 16, 26, (73, 52, 104, 255))
        c.outline(8, 3, 16, 26, PALETTE["gold2"], 2)
        c.circle(16, 13, 5, PALETTE["lilac"], fill=(116, 73, 167, 255))
        c.polygon([(16, 16), (21, 23), (16, 27), (11, 23)], PALETTE["goldlight"])
    elif kind == "flame":
        c.polygon([(16, 2), (24, 13), (23, 22), (16, 30), (8, 23), (9, 14)],
                  (155, 62, 26, 255))
        c.polygon([(16, 10), (21, 17), (18, 26), (12, 22), (13, 16)],
                  (255, 180, 66, 255))
        c.line(16, 14, 16, 25, PALETTE["goldlight"], 2)
    elif kind == "shard":
        c.polygon([(16, 2), (26, 11), (21, 27), (12, 30), (6, 15)],
                  (106, 59, 160, 255))
        c.polygon([(16, 5), (22, 12), (19, 24), (13, 26), (9, 15)],
                  (177, 135, 224, 255))
        c.line(13, 10, 16, 20, PALETTE["white"], 2)
    elif kind == "chronometer":
        c.circle(16, 17, 12, PALETTE["gold2"])
        c.circle(16, 17, 9, (21, 31, 52, 255), fill=(21, 31, 52, 255))
        c.rect(13, 1, 6, 4, PALETTE["gold"])
        c.line(16, 17, 16, 8, PALETTE["white"], 2)
        c.line(16, 17, 22, 20, PALETTE["lilac"], 2)
    elif kind == "bow":
        c.line(10, 3, 10, 29, PALETTE["gold2"], 3)
        c.line(10, 3, 25, 16, PALETTE["gold"], 2)
        c.line(25, 16, 10, 29, PALETTE["gold"], 2)
        c.line(10, 3, 10, 29, PALETTE["white"], 1)
        c.line(7, 16, 29, 16, (188, 207, 230, 255), 2)
        c.polygon([(29, 16), (23, 12), (23, 20)], PALETTE["white"])
    elif kind == "profile":
        c.circle(16, 16, 14, PALETTE["gold2"])
        c.circle(16, 16, 11, (32, 35, 62, 255), fill=(32, 35, 62, 255))
        c.circle(16, 11, 5, (222, 161, 116, 255), fill=(222, 161, 116, 255))
        c.polygon([(6, 27), (8, 19), (12, 16), (20, 16), (24, 19), (26, 27)], PALETTE["lilac"])
    elif kind == "class":
        c.polygon([(16, 2), (28, 9), (25, 23), (16, 30), (7, 23), (4, 9)], PALETTE["gold2"])
        c.polygon([(16, 5), (24, 10), (22, 21), (16, 26), (10, 21), (8, 10)], (48, 36, 77, 255))
        c.polygon([(16, 7), (19, 14), (25, 16), (19, 19), (16, 26), (13, 19), (7, 16), (13, 14)], PALETTE["lilac"])
    elif kind == "skills":
        c.polygon([(3, 7), (14, 9), (16, 27), (5, 24)], (93, 59, 135, 255))
        c.polygon([(29, 7), (18, 9), (16, 27), (27, 24)], (58, 96, 139, 255))
        c.line(16, 10, 16, 27, PALETTE["gold2"], 2)
        c.circle(16, 7, 4, PALETTE["goldlight"], fill=PALETTE["gold"])
    elif kind == "quests":
        c.rect(6, 3, 20, 26, (164, 121, 79, 255))
        c.outline(6, 3, 20, 26, PALETTE["gold2"], 2)
        for y in (11, 17, 23):
            c.circle(11, y, 2, PALETTE["violet"], fill=PALETTE["violet"])
            c.line(15, y, 23, y, (245, 215, 164, 255), 1)
    elif kind == "abilities":
        c.line(8, 27, 23, 6, PALETTE["gold2"], 3)
        c.circle(24, 6, 5, PALETTE["lilac"], fill=(104, 180, 230, 255))
        c.polygon([(10, 8), (13, 14), (20, 16), (13, 19),
                   (10, 26), (7, 19), (1, 16), (7, 14)], PALETTE["goldlight"])
    elif kind == "home":
        c.circle(16, 16, 14, PALETTE["gold2"])
        c.circle(16, 16, 11, (27, 35, 60, 255), fill=(27, 35, 60, 255))
        c.circle(16, 16, 8, PALETTE["violet"])
        c.polygon([(16, 3), (19, 14), (28, 16), (19, 18),
                   (16, 29), (13, 18), (4, 16), (13, 14)], PALETTE["white"])
        c.circle(16, 16, 3, PALETTE["goldlight"], fill=PALETTE["gold"])
    elif kind == "economy":
        c.circle(16, 16, 14, PALETTE["gold2"])
        c.circle(16, 16, 10, (60, 40, 83, 255), fill=(60, 40, 83, 255))
        c.circle(16, 16, 7, PALETTE["goldlight"])
        c.line(16, 7, 16, 25, PALETTE["gold"], 2)
        c.line(8, 16, 24, 16, PALETTE["gold"], 2)
    elif kind == "shop":
        c.rect(3, 11, 26, 18, (106, 65, 38, 255))
        c.rect(2, 7, 28, 7, PALETTE["gold2"])
        c.outline(3, 11, 26, 18, (231, 185, 101, 255), 2)
        c.circle(16, 19, 4, PALETTE["lilac"], fill=(98, 75, 144, 255))
        c.rect(13, 11, 6, 5, PALETTE["goldlight"])
    elif kind == "mage":
        c.circle(16, 16, 14, PALETTE["gold2"])
        c.circle(16, 16, 10, (25, 36, 68, 255), fill=(25, 36, 68, 255))
        c.polygon([(16, 2), (24, 16), (16, 30), (8, 16)], (82, 177, 226, 255))
        c.polygon([(16, 6), (20, 16), (16, 25), (12, 16)], PALETTE["white"])
    elif kind == "warrior":
        c.polygon([(16, 2), (28, 10), (24, 26), (16, 30), (8, 26), (4, 10)], PALETTE["gold2"])
        c.line(8, 24, 24, 8, PALETTE["white"], 4)
        c.line(6, 22, 14, 28, PALETTE["gold"], 3)
        c.circle(8, 25, 3, PALETTE["lilac"], fill=PALETTE["gold"])
    elif kind == "ranger":
        c.line(9, 3, 9, 29, PALETTE["gold2"], 2)
        c.line(9, 3, 25, 16, PALETTE["lilac"], 3)
        c.line(25, 16, 9, 29, PALETTE["lilac"], 3)
        c.line(8, 3, 8, 29, PALETTE["white"], 1)
        c.line(7, 16, 29, 16, (198, 220, 246, 255), 2)
        c.polygon([(29,16),(23,12),(23,20)], PALETTE["goldlight"])
    elif kind == "guardian":
        c.polygon([(16, 2), (27, 7), (25, 20), (16, 30), (7, 20), (5, 7)], PALETTE["gold2"])
        c.polygon([(16, 5), (23, 9), (22, 19), (16, 26), (10, 19), (9, 9)], (44, 125, 143, 255))
        c.line(16, 7, 16, 25, PALETTE["white"], 2)
        c.line(10, 12, 22, 12, PALETTE["lilac"], 2)
    elif kind == "quest_hunt":
        c.circle(16, 16, 14, (66, 35, 42, 255))
        c.circle(16, 16, 10, PALETTE["gold2"])
        c.polygon([(16,5),(20,12),(27,16),(20,20),(16,27),(12,20),(5,16),(12,12)], (175,67,79,255))
        c.circle(16, 16, 3, PALETTE["white"], fill=PALETTE["white"])
    elif kind == "quest_slayer":
        c.line(7, 25, 25, 7, PALETTE["white"], 4)
        c.line(5, 21, 13, 29, PALETTE["gold2"], 3)
        c.circle(7, 25, 3, PALETTE["lilac"], fill=PALETTE["gold"])
        c.polygon([(25,4),(29,8),(24,10)], PALETTE["goldlight"])
    elif kind == "quest_miner":
        c.polygon([(16, 3),(27,11),(22,27),(11,27),(5,14)], (69,110,164,255))
        c.polygon([(16, 7),(23,13),(19,23),(12,24),(8,15)], (152,201,240,255))
        c.line(11,10,17,22,PALETTE["white"],2)
    elif kind == "quest_lumber":
        c.rect(12, 5, 8, 23, (129,83,48,255))
        c.rect(4, 8, 24, 8, PALETTE["gold2"])
        c.polygon([(5,8),(15,2),(27,8),(20,16),(7,16)], (170,181,103,255))
        c.line(15, 8, 15, 24, (224,181,113,255), 2)
    elif kind == "quest_farmer":
        c.polygon([(16,29),(12,18),(16,9),(20,18)], (97,174,94,255))
        c.line(16, 28, 16, 9, PALETTE["goldlight"], 2)
        c.line(16, 17, 7, 12, (128,205,118,255), 3)
        c.line(16, 17, 25, 12, (128,205,118,255), 3)
        c.circle(16, 7, 4, PALETTE["gold"], fill=PALETTE["gold2"])
    elif kind == "quest_fisher":
        c.line(8, 4, 24, 4, PALETTE["gold2"], 2)
        c.line(22, 4, 22, 17, PALETTE["gold2"], 2)
        c.line(22, 17, 16, 22, PALETTE["white"], 1)
        c.polygon([(16,22),(10,18),(10,25)], (104,184,226,255))
        c.polygon([(3,24),(8,19),(13,24),(8,29)], (83,158,215,255))
    elif kind == "ability_arcane":
        c.polygon([(16,2),(21,12),(30,16),(21,20),(16,30),(11,20),(2,16),(11,12)], (106,81,188,255))
        c.circle(16,16,6,(97,187,237,255),fill=(97,187,237,255))
        c.circle(16,16,2,PALETTE["white"],fill=PALETTE["white"])
    elif kind == "ability_blade":
        c.line(7,25,25,7,PALETTE["white"],4)
        c.line(8,21,15,28,PALETTE["gold2"],3)
        c.line(4,21,12,29,PALETTE["lilac"],2)
        c.circle(7,25,2,PALETTE["goldlight"],fill=PALETTE["goldlight"])
    elif kind == "ability_arrow":
        c.line(5,27,27,5,(190,215,241,255),2)
        c.polygon([(28,4),(20,6),(26,12)], PALETTE["goldlight"])
        c.polygon([(5,27),(8,19),(13,24)], PALETTE["lilac"])
        c.line(8,24,24,8,PALETTE["white"],1)
    elif kind == "ability_aegis":
        c.polygon([(16,2),(27,7),(25,20),(16,30),(7,20),(5,7)],PALETTE["gold2"])
        c.polygon([(16,6),(23,10),(21,19),(16,25),(11,19),(9,10)],(64,145,158,255))
        c.circle(16,15,4,PALETTE["white"],fill=PALETTE["lilac"])
    elif kind == "path_flow":
        c.polygon([(16,2),(25,14),(22,24),(16,30),(10,24),(7,14)],(76,162,222,255))
        c.polygon([(16,7),(21,15),(16,24),(11,15)],(193,235,255,255))
        c.line(12,13,16,8,PALETTE["white"],2)
    elif kind == "path_seal":
        c.circle(16,16,14,PALETTE["gold2"])
        c.polygon([(16,4),(25,9),(23,20),(16,27),(9,20),(7,9)],(61,145,151,255))
        c.line(16,7,16,24,PALETTE["white"],2)
        c.line(10,15,22,15,PALETTE["goldlight"],2)
    elif kind == "path_resonance":
        c.polygon([(16,2),(27,12),(22,28),(10,28),(5,12)],(106,73,170,255))
        c.polygon([(16,6),(22,13),(19,24),(13,24),(10,13)],(205,153,236,255))
        c.line(11,10,16,20,PALETTE["white"],2)
    elif kind == "path_tempo":
        c.circle(16,17,12,PALETTE["gold2"])
        c.circle(16,17,9,(28,35,59,255),fill=(28,35,59,255))
        c.rect(13,1,6,4,PALETTE["gold"])
        c.line(16,17,16,8,PALETTE["white"],2)
        c.line(16,17,23,20,PALETTE["lilac"],2)
    elif kind == "experience":
        c.circle(16, 16, 14, PALETTE["gold2"])
        c.circle(16, 16, 10, (35, 32, 62, 255), fill=(35, 32, 62, 255))
        c.polygon([(16,4),(20,12),(28,16),(20,20),(16,28),(12,20),(4,16),(12,12)], (160,115,214,255))
        c.circle(16, 16, 4, PALETTE["goldlight"], fill=PALETTE["goldlight"])
    elif kind == "locked":
        c.rect(5,14,22,15,(45,43,65,255))
        c.outline(5,14,22,15,PALETTE["gold2"],2)
        c.circle(16,11,8,PALETTE["lilac"])
        c.circle(16,11,5,(26,27,46,255),fill=(26,27,46,255))
        c.rect(13,10,6,7,PALETTE["gold2"])
        c.circle(16,17,2,PALETTE["white"],fill=PALETTE["white"])
    elif kind == "close":
        c.circle(16, 16, 14, (48, 30, 66, 255))
        c.circle(16, 16, 11, PALETTE["gold2"])
        c.line(10, 10, 22, 22, (255, 210, 218, 255), 4)
        c.line(22, 10, 10, 22, (255, 210, 218, 255), 4)
        c.circle(16, 16, 2, PALETTE["white"], fill=PALETTE["white"])
    elif kind == "shop_food":
        c.circle(16, 16, 13, (63, 38, 46, 255))
        c.polygon([(7,22),(9,11),(17,6),(24,10),(26,19),(19,25)], (176,81,62,255))
        c.polygon([(10,20),(12,12),(18,9),(22,12),(22,18),(17,22)], (230,149,96,255))
        c.line(22, 21, 28, 27, PALETTE["white"], 3)
    elif kind == "shop_arrows":
        c.circle(16, 16, 14, (34, 44, 69, 255))
        for x in (10,16,22):
            c.line(x-5,24,x+5,8,PALETTE["gold2"],2)
            c.polygon([(x+5,8),(x-1,10),(x+4,14)], PALETTE["white"])
    elif kind == "shop_pearl":
        c.circle(16, 16, 14, (31, 48, 75, 255))
        c.circle(16, 16, 10, (74, 154, 203, 255))
        c.circle(13, 12, 4, (220, 248, 255, 255), fill=(220, 248, 255, 255))
        c.circle(19, 20, 3, PALETTE["lilac"], fill=PALETTE["lilac"])
    elif kind == "shop_carrot":
        c.polygon([(16,5),(24,10),(19,27),(14,30),(9,13)], (225,117,44,255))
        c.line(16,7,12,2,(93,183,92,255),3)
        c.line(17,7,21,2,(126,204,108,255),3)
        c.line(17,14,19,16,(255,202,112,255),1)
    elif kind == "shop_apple":
        c.circle(16,18,11,(183,54,67,255),fill=(183,54,67,255))
        c.circle(10,15,6,(230,83,86,255),fill=(230,83,86,255))
        c.line(16,8,18,2,(111,78,44,255),3)
        c.polygon([(18,7),(27,5),(23,12)],(99,183,86,255))
        c.circle(12,12,2,(255,190,152,255),fill=(255,190,152,255))
    elif kind == "shop_xp":
        c.polygon([(10,4),(22,4),(23,21),(16,29),(9,21)], (69,131,189,255))
        c.rect(11,3,10,4,PALETTE["gold2"])
        c.polygon([(16,9),(20,16),(16,23),(12,16)], (191,229,255,255))
        c.circle(16,16,3,PALETTE["white"],fill=PALETTE["white"])
    elif kind == "shop_blade":
        c.circle(16,16,14,(47,37,68,255))
        c.line(7,25,25,7,(207,226,244,255),5)
        c.line(9,23,25,7,PALETTE["white"],2)
        c.line(5,20,13,28,PALETTE["gold2"],3)
        c.circle(7,25,2,PALETTE["goldlight"],fill=PALETTE["goldlight"])
    elif kind == "shop_shield":
        c.circle(16,16,14,(38,52,72,255))
        c.polygon([(16,3),(27,7),(25,20),(16,29),(7,20),(5,7)],PALETTE["gold2"])
        c.polygon([(16,6),(23,9),(21,19),(16,25),(11,19),(9,9)],(57,137,160,255))
        c.line(16,8,16,24,PALETTE["white"],2)
        c.line(10,13,22,13,PALETTE["lilac"],2)
    elif kind == "shop_diamond":
        c.circle(16,16,14,(32,53,80,255))
        c.polygon([(7,11),(12,4),(21,4),(26,11),(16,28)],(63,177,221,255))
        c.polygon([(12,7),(20,7),(16,23)],(202,245,255,255))
        c.line(12,7,16,23,PALETTE["white"],1)
    elif kind == "shop_fish":
        c.circle(16,16,14,(30,55,79,255))
        c.ellipse = None
        c.polygon([(4,16),(11,8),(21,9),(27,16),(21,23),(11,24)],(77,159,204,255))
        c.polygon([(5,16),(0,10),(0,22)],(114,193,222,255))
        c.circle(22,14,2,PALETTE["white"],fill=PALETTE["white"])
        c.circle(22,14,1,(20,36,55,255),fill=(20,36,55,255))
    elif kind == "shop_rocket":
        c.circle(16,16,14,(43,36,75,255))
        c.polygon([(16,2),(23,12),(21,23),(16,28),(11,23),(9,12)],(152,164,195,255))
        c.circle(16,12,4,(104,190,232,255),fill=(104,190,232,255))
        c.polygon([(11,21),(6,28),(13,25)],(238,124,64,255))
        c.polygon([(21,21),(26,28),(19,25)],(238,124,64,255))
    elif kind == "shop_totem":
        c.circle(16,16,14,(49,39,65,255))
        c.rect(8,5,16,23,(167,128,63,255))
        c.outline(8,5,16,23,PALETTE["goldlight"],2)
        c.circle(12,13,2,(87,195,185,255),fill=(87,195,185,255))
        c.circle(20,13,2,(87,195,185,255),fill=(87,195,185,255))
        c.polygon([(16,15),(21,21),(16,26),(11,21)],PALETTE["lilac"])
    elif kind == "mana":
        c.circle(16,16,14,(43,37,78,255))
        c.circle(16,16,10,(68,111,183,255))
        c.polygon([(16,4),(24,15),(16,28),(8,15)],(110,208,245,255))
        c.line(12,14,16,8,PALETTE["white"],2)
        c.circle(16,17,3,PALETTE["goldlight"],fill=PALETTE["goldlight"])
    elif kind == "menu_token":
        c.circle(16, 16, 14, PALETTE["gold2"])
        c.circle(16, 16, 11, (42, 30, 68, 255))
        c.circle(16, 16, 8, PALETTE["violet"])
        c.polygon([(16, 3), (19, 13), (29, 16), (19, 19),
                   (16, 29), (13, 19), (3, 16), (13, 13)], PALETTE["goldlight"])
    elif kind == "quick_item":
        c.circle(16, 16, 14, (55, 39, 86, 255))
        c.circle(16, 16, 11, PALETTE["gold2"])
        for x, y, col in [(10, 10, (230, 111, 79, 255)),
                          (22, 10, (94, 182, 230, 255)),
                          (10, 22, (163, 109, 206, 255)),
                          (22, 22, (133, 206, 125, 255))]:
            c.circle(x, y, 4, PALETTE["white"])
            c.circle(x, y, 3, col, fill=col)
    return c.png()

def main():
    assets = {
        "pack.mcmeta": json.dumps({"pack": {"pack_format": 46,
            "description": "Astral Nexus RPG - custom fantasy GUI textures"}}, ensure_ascii=False).encode(),
        "pack.png": icon_png("nexus"),
        "assets/astral/font/gui.json": json.dumps({"providers": [
            {"type": "space", "advances": {"\uf808": -8}},
            {"type": "bitmap", "file": "astral:font/equipment.png",
             "ascent": 13, "height": 222, "chars": ["\ue000"]},
            {"type": "bitmap", "file": "astral:font/skills.png",
             "ascent": 13, "height": 222, "chars": ["\ue001"]},
            {"type": "bitmap", "file": "astral:font/profile.png",
             "ascent": 13, "height": 222, "chars": ["\ue002"]},
            {"type": "bitmap", "file": "astral:font/class.png",
             "ascent": 13, "height": 222, "chars": ["\ue003"]},
            {"type": "bitmap", "file": "astral:font/quests.png",
             "ascent": 13, "height": 222, "chars": ["\ue004"]},
            {"type": "bitmap", "file": "astral:font/abilities.png",
             "ascent": 13, "height": 222, "chars": ["\ue005"]},
            {"type": "bitmap", "file": "astral:font/economy.png",
             "ascent": 13, "height": 222, "chars": ["\ue006"]},
            {"type": "bitmap", "file": "astral:font/shop.png",
             "ascent": 13, "height": 222, "chars": ["\ue007"]}
        ]}, ensure_ascii=False).encode(),
        "assets/astral/textures/font/equipment.png": equipment_art(),
        "assets/astral/textures/font/skills.png": skills_art(),
        "assets/astral/textures/font/submenu.png": background(125, True).png(),
        "assets/astral/textures/font/profile.png": tab_art("profile"),
        "assets/astral/textures/font/class.png": tab_art("class"),
        "assets/astral/textures/font/quests.png": tab_art("quests"),
        "assets/astral/textures/font/abilities.png": tab_art("abilities"),
        "assets/astral/textures/font/economy.png": tab_art("economy"),
        "assets/astral/textures/font/shop.png": tab_art("shop"),
        "assets/astral/textures/item/nexus.png": icon_png("nexus"),
        "assets/astral/textures/item/sword.png": icon_png("sword"),
        "assets/astral/textures/item/mana.png": icon_png("mana"),
        "assets/astral/textures/item/shield.png": icon_png("shield"),
        "assets/astral/textures/item/orb.png": icon_png("orb"),
        "assets/astral/textures/item/ring.png": icon_png("ring"),
        "assets/astral/textures/item/amulet.png": icon_png("amulet"),
        "assets/astral/textures/item/belt.png": icon_png("belt"),
        "assets/astral/textures/item/cloak.png": icon_png("cloak"),
        "assets/astral/textures/item/rune.png": icon_png("rune"),
        "assets/astral/textures/item/relic.png": icon_png("relic"),
        "assets/astral/textures/item/flame.png": icon_png("flame"),
        "assets/astral/textures/item/shard.png": icon_png("shard"),
        "assets/astral/textures/item/chronometer.png": icon_png("chronometer"),
        "assets/astral/textures/item/bow.png": icon_png("bow"),
        "assets/astral/textures/item/home.png": icon_png("home"),
        "assets/astral/textures/item/profile.png": icon_png("profile"),
        "assets/astral/textures/item/class.png": icon_png("class"),
        "assets/astral/textures/item/skills.png": icon_png("skills"),
        "assets/astral/textures/item/quests.png": icon_png("quests"),
        "assets/astral/textures/item/abilities.png": icon_png("abilities"),
        "assets/astral/textures/item/economy.png": icon_png("economy"),
        "assets/astral/textures/item/shop.png": icon_png("shop"),
        "assets/astral/textures/item/locked.png": icon_png("locked"),
        "assets/astral/textures/item/experience.png": icon_png("experience"),
        "assets/astral/textures/item/mage.png": icon_png("mage"),
        "assets/astral/textures/item/warrior.png": icon_png("warrior"),
        "assets/astral/textures/item/ranger.png": icon_png("ranger"),
        "assets/astral/textures/item/guardian.png": icon_png("guardian"),
        "assets/astral/textures/item/quest_hunt.png": icon_png("quest_hunt"),
        "assets/astral/textures/item/quest_slayer.png": icon_png("quest_slayer"),
        "assets/astral/textures/item/quest_miner.png": icon_png("quest_miner"),
        "assets/astral/textures/item/quest_lumber.png": icon_png("quest_lumber"),
        "assets/astral/textures/item/quest_farmer.png": icon_png("quest_farmer"),
        "assets/astral/textures/item/quest_fisher.png": icon_png("quest_fisher"),
        "assets/astral/textures/item/ability_arcane.png": icon_png("ability_arcane"),
        "assets/astral/textures/item/ability_blade.png": icon_png("ability_blade"),
        "assets/astral/textures/item/ability_arrow.png": icon_png("ability_arrow"),
        "assets/astral/textures/item/ability_aegis.png": icon_png("ability_aegis"),
        "assets/astral/textures/item/path_flow.png": icon_png("path_flow"),
        "assets/astral/textures/item/path_seal.png": icon_png("path_seal"),
        "assets/astral/textures/item/path_resonance.png": icon_png("path_resonance"),
        "assets/astral/textures/item/path_tempo.png": icon_png("path_tempo"),
        "assets/astral/textures/item/menu_token.png": icon_png("menu_token"),
        "assets/astral/textures/item/quick_item.png": icon_png("quick_item"),
        "assets/astral/models/item/nexus.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/nexus"}}',
        "assets/astral/models/item/sword.json": b'{"parent":"minecraft:item/handheld","textures":{"layer0":"astral:item/sword"}}',
        "assets/astral/models/item/mana.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/mana"}}',
        "assets/astral/models/item/shield.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/shield"}}',
        "assets/astral/models/item/orb.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/orb"}}',
        "assets/astral/models/item/ring.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/ring"}}',
        "assets/astral/models/item/amulet.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/amulet"}}',
        "assets/astral/models/item/belt.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/belt"}}',
        "assets/astral/models/item/cloak.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/cloak"}}',
        "assets/astral/models/item/rune.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/rune"}}',
        "assets/astral/models/item/relic.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/relic"}}',
        "assets/astral/models/item/flame.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/flame"}}',
        "assets/astral/models/item/shard.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/shard"}}',
        "assets/astral/models/item/chronometer.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/chronometer"}}',
        "assets/astral/models/item/bow.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/bow"}}',
        "assets/astral/models/item/home.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/home"}}',
        "assets/astral/models/item/profile.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/profile"}}',
        "assets/astral/models/item/class.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/class"}}',
        "assets/astral/models/item/skills.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/skills"}}',
        "assets/astral/models/item/quests.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/quests"}}',
        "assets/astral/models/item/abilities.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/abilities"}}',
        "assets/astral/models/item/economy.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/economy"}}',
        "assets/astral/models/item/shop.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/shop"}}',
        "assets/astral/models/item/locked.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/locked"}}',
        "assets/astral/models/item/experience.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/experience"}}',
        "assets/astral/models/item/mage.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/mage"}}',
        "assets/astral/models/item/warrior.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/warrior"}}',
        "assets/astral/models/item/ranger.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/ranger"}}',
        "assets/astral/models/item/guardian.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/guardian"}}',
        "assets/astral/models/item/quest_hunt.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/quest_hunt"}}',
        "assets/astral/models/item/quest_slayer.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/quest_slayer"}}',
        "assets/astral/models/item/quest_miner.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/quest_miner"}}',
        "assets/astral/models/item/quest_lumber.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/quest_lumber"}}',
        "assets/astral/models/item/quest_farmer.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/quest_farmer"}}',
        "assets/astral/models/item/quest_fisher.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/quest_fisher"}}',
        "assets/astral/models/item/ability_arcane.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/ability_arcane"}}',
        "assets/astral/models/item/ability_blade.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/ability_blade"}}',
        "assets/astral/models/item/ability_arrow.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/ability_arrow"}}',
        "assets/astral/models/item/ability_aegis.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/ability_aegis"}}',
        "assets/astral/models/item/path_flow.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/path_flow"}}',
        "assets/astral/models/item/path_seal.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/path_seal"}}',
        "assets/astral/models/item/path_resonance.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/path_resonance"}}',
        "assets/astral/models/item/path_tempo.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/path_tempo"}}',
        "assets/astral/models/item/menu_token.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/menu_token"}}',
        "assets/astral/models/item/quick_item.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"astral:item/quick_item"}}',
        "assets/minecraft/models/item/nether_star.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/nether_star"},"overrides":[{"predicate":{"custom_model_data":1001},"model":"astral:item/nexus"},{"predicate":{"custom_model_data":1307},"model":"astral:item/menu_token"}]}',
        "assets/minecraft/models/item/iron_sword.json": b'{"parent":"minecraft:item/handheld","textures":{"layer0":"minecraft:item/iron_sword"},"overrides":[{"predicate":{"custom_model_data":1002},"model":"astral:item/sword"}]}',
        "assets/minecraft/models/item/lapis_lazuli.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/lapis_lazuli"},"overrides":[{"predicate":{"custom_model_data":1003},"model":"astral:item/mana"}]}',
        "assets/minecraft/models/item/heart_of_the_sea.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/heart_of_the_sea"},"overrides":[{"predicate":{"custom_model_data":1101},"model":"astral:item/orb"}]}',
        "assets/minecraft/models/item/rabbit_foot.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/rabbit_foot"},"overrides":[{"predicate":{"custom_model_data":1102},"model":"astral:item/ring"}]}',
        "assets/minecraft/models/item/nautilus_shell.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/nautilus_shell"},"overrides":[{"predicate":{"custom_model_data":1103},"model":"astral:item/amulet"}]}',
        "assets/minecraft/models/item/emerald.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/emerald"},"overrides":[{"predicate":{"custom_model_data":1104},"model":"astral:item/belt"}]}',
        "assets/minecraft/models/item/phantom_membrane.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/phantom_membrane"},"overrides":[{"predicate":{"custom_model_data":1105},"model":"astral:item/cloak"}]}',
        "assets/minecraft/models/item/echo_shard.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/echo_shard"},"overrides":[{"predicate":{"custom_model_data":1106},"model":"astral:item/rune"}]}',
        "assets/minecraft/models/item/totem_of_undying.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/totem_of_undying"},"overrides":[{"predicate":{"custom_model_data":1107},"model":"astral:item/relic"}]}',
        "assets/minecraft/models/item/prismarine_shard.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/prismarine_shard"},"overrides":[{"predicate":{"custom_model_data":1108},"model":"astral:item/shield"}]}',
        "assets/minecraft/models/item/blaze_powder.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/blaze_powder"},"overrides":[{"predicate":{"custom_model_data":1109},"model":"astral:item/flame"}]}',
        "assets/minecraft/models/item/amethyst_shard.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/amethyst_shard"},"overrides":[{"predicate":{"custom_model_data":1110},"model":"astral:item/shard"}]}',
        "assets/minecraft/models/item/clock.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/clock"},"overrides":[{"predicate":{"custom_model_data":1111},"model":"astral:item/chronometer"}]}',
        "assets/minecraft/models/item/feather.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/feather"},"overrides":[{"predicate":{"custom_model_data":1112},"model":"astral:item/bow"}]}',
        "assets/minecraft/models/item/compass.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/compass"},"overrides":[{"predicate":{"custom_model_data":1301},"model":"astral:item/home"}]}',
        "assets/minecraft/models/item/paper.json": json.dumps({
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "minecraft:item/paper"},
            "overrides": [
                {"predicate": {"custom_model_data": 1301}, "model": "astral:item/home"},
                {"predicate": {"custom_model_data": 1303}, "model": "astral:item/class"},
                {"predicate": {"custom_model_data": 1304}, "model": "astral:item/skills"},
                {"predicate": {"custom_model_data": 1305}, "model": "astral:item/quests"},
                {"predicate": {"custom_model_data": 1306}, "model": "astral:item/abilities"},
                {"predicate": {"custom_model_data": 1309}, "model": "astral:item/economy"},
                {"predicate": {"custom_model_data": 1310}, "model": "astral:item/shop"},
                {"predicate": {"custom_model_data": 1302}, "model": "astral:item/profile"},
                {"predicate": {"custom_model_data": 1311}, "model": "astral:item/mage"},
                {"predicate": {"custom_model_data": 1312}, "model": "astral:item/warrior"},
                {"predicate": {"custom_model_data": 1313}, "model": "astral:item/ranger"},
                {"predicate": {"custom_model_data": 1314}, "model": "astral:item/guardian"},
                {"predicate": {"custom_model_data": 1315}, "model": "astral:item/locked"},
                {"predicate": {"custom_model_data": 1316}, "model": "astral:item/experience"},
                {"predicate": {"custom_model_data": 1321}, "model": "astral:item/quest_hunt"},
                {"predicate": {"custom_model_data": 1322}, "model": "astral:item/quest_slayer"},
                {"predicate": {"custom_model_data": 1323}, "model": "astral:item/quest_miner"},
                {"predicate": {"custom_model_data": 1324}, "model": "astral:item/quest_lumber"},
                {"predicate": {"custom_model_data": 1325}, "model": "astral:item/quest_farmer"},
                {"predicate": {"custom_model_data": 1326}, "model": "astral:item/quest_fisher"},
                {"predicate": {"custom_model_data": 1331}, "model": "astral:item/ability_arcane"},
                {"predicate": {"custom_model_data": 1332}, "model": "astral:item/ability_blade"},
                {"predicate": {"custom_model_data": 1333}, "model": "astral:item/ability_arrow"},
                {"predicate": {"custom_model_data": 1334}, "model": "astral:item/ability_aegis"},
                {"predicate": {"custom_model_data": 1335}, "model": "astral:item/path_flow"},
                {"predicate": {"custom_model_data": 1336}, "model": "astral:item/path_seal"},
                {"predicate": {"custom_model_data": 1337}, "model": "astral:item/path_resonance"},
                {"predicate": {"custom_model_data": 1338}, "model": "astral:item/path_tempo"}
            ]
        }, separators=(",", ":")).encode(),
        "assets/minecraft/models/item/enchanted_book.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/enchanted_book"},"overrides":[{"predicate":{"custom_model_data":1304},"model":"astral:item/skills"}]}',
        "assets/minecraft/models/item/writable_book.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/writable_book"},"overrides":[{"predicate":{"custom_model_data":1305},"model":"astral:item/quests"}]}',
        "assets/minecraft/models/item/blaze_rod.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/blaze_rod"},"overrides":[{"predicate":{"custom_model_data":1306},"model":"astral:item/abilities"}]}',
        "assets/minecraft/models/item/recovery_compass.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/recovery_compass"},"overrides":[{"predicate":{"custom_model_data":1308},"model":"astral:item/quick_item"}]}',
        "assets/minecraft/models/item/gold_ingot.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/gold_ingot"},"overrides":[{"predicate":{"custom_model_data":1309},"model":"astral:item/economy"}]}',
        "assets/minecraft/models/item/chest.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/chest"},"overrides":[{"predicate":{"custom_model_data":1310},"model":"astral:item/shop"}]}',
        "assets/minecraft/models/item/gold_ingot.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/gold_ingot"},"overrides":[{"predicate":{"custom_model_data":1309},"model":"astral:item/economy"}]}',
        "assets/minecraft/models/item/chest.json": b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/chest"},"overrides":[{"predicate":{"custom_model_data":1310},"model":"astral:item/shop"}]}',
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as pack:
        for name, data in assets.items():
            pack.writestr(name, data)
    # The resource pack is placed only inside the plugin resources.
    # The user installs a single JAR; no second ZIP is created or required.
    print(f"Embedded resource pack generated: {OUT} ({OUT.stat().st_size} bytes, {len(assets)} files)")

if __name__ == "__main__":
    main()
