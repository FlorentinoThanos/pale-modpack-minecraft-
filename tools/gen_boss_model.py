#!/usr/bin/env python3
"""Builds the draft model of the Patchwork Abomination: geometry, animations, textures and the heart's icon.

A stand-in until there is art (see the README, "Replacing the boss model"): python3 tools/gen_boss_model.py.
Needs Pillow. The numbers at the top must match com.example.thepale.finale.boss.Limbs.
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "thepale")

PX = 16                      # pixels in a block
R = 4.5 * PX                 # the body's radius; its lowest point is the model's origin
SEGMENTS = 8
SEG = 2 * PX                 # length of a limb segment
AZIMUTH = [30, 90, 150, 210, 270, 330, 305, 55]
ELEVATION = [-38, -38, -38, -38, -38, -38, 22, 22]
NAMES = ["tentacle%d" % (i + 1) for i in range(6)] + ["face", "hand"]

TEX = 256
# Regions of the texture: (u, v, width, height).
SKIN_A = (0, 0, 72, 72)
SKIN_B = (72, 0, 72, 72)
SKIN_TOP = (144, 0, 72, 72)
TENT = (0, 72, 16, 32)
TENT_END = (16, 72, 16, 16)
EYE = (32, 72, 24, 24)
LID = (56, 72, 24, 12)
SEAM = (80, 72, 14, 28)
SLIT = (96, 72, 22, 4)
FACE = (0, 136, 24, 30)
STONE = (24, 136, 24, 30)
RED = (48, 136, 16, 16)
CLAW = (64, 136, 8, 8)


def face(region):
    u, v, w, h = region
    return {"uv": [u, v], "uv_size": [w, h]}


def all_faces(region, **override):
    faces = {side: face(region) for side in ("north", "south", "east", "west", "up", "down")}
    faces.update({side: face(r) for side, r in override.items()})
    return faces


def cube(x0, y0, z0, w, h, d, uv):
    return {"origin": [round(x0, 2), round(y0, 2), round(z0, 2)], "size": [w, h, d], "uv": uv}


def body_cubes():
    """A ball out of nine slabs; each slab is three boxes that together make a rough octagon."""
    cubes = []
    for i in range(9):
        y0 = i * 16
        offset = y0 + 8 - R
        half = math.sqrt(max(R * R - offset * offset, 0))
        for hx, hz in ((1.0, 0.42), (0.42, 1.0), (0.76, 0.76)):
            wx, wz = round(half * hx), round(half * hz)
            v = (144 - (y0 + 16)) / 2
            side_x = {"uv": [SKIN_A[0] + (R - wx) / 2, SKIN_A[1] + v], "uv_size": [wx, 8]}
            side_z = {"uv": [SKIN_B[0] + (R - wz) / 2, SKIN_B[1] + v], "uv_size": [wz, 8]}
            top = {"uv": [SKIN_TOP[0] + (R - wx) / 2, SKIN_TOP[1] + (R - wz) / 2], "uv_size": [wx, wz]}
            cubes.append(cube(-wx, y0, -wz, 2 * wx, 16, 2 * wz,
                              {"north": side_x, "south": side_x, "east": side_z, "west": side_z, "up": top, "down": top}))
    return cubes


def anchor(limb):
    """Where a limb leaves the body, in model pixels (the front of the model is -Z)."""
    az, el = math.radians(AZIMUTH[limb]), math.radians(ELEVATION[limb])
    reach = R * 0.9
    return (-math.sin(az) * math.cos(el) * reach, R + math.sin(el) * reach, -math.cos(az) * math.cos(el) * reach)


def geometry():
    bones = [{"name": "body", "pivot": [0, R, 0], "cubes": body_cubes()}]
    # Glowing slits, on the front.
    slits = []
    for x, y, tilt in ((-34, 100, 0), (6, 82, 0), (-40, 62, 0), (30, 108, 0)):
        depth = math.sqrt(max(R * R - (y - R) ** 2 - x * x, 0))
        slits.append(cube(x - 11, y - 2, -depth - 5, 22, 4, 4, all_faces(SLIT)))
    bones.append({"name": "slits", "parent": "body", "pivot": [0, R, 0], "cubes": slits})
    # The main eye, a little below the middle of the front, behind its lids and the seam.
    ey, ez = R - 1.3 * PX, -(R - 2)
    bones.append({"name": "eye_main", "parent": "body", "pivot": [0, ey, ez],
                  "cubes": [cube(-18, ey - 18, ez - 4, 36, 36, 6, all_faces(SKIN_A, north=EYE))]})
    bones.append({"name": "eyelid_upper", "parent": "body", "pivot": [0, ey + 19, ez - 4],
                  "cubes": [cube(-19, ey, ez - 6, 38, 19, 8, all_faces(LID))]})
    bones.append({"name": "eyelid_lower", "parent": "body", "pivot": [0, ey - 19, ez - 4],
                  "cubes": [cube(-19, ey - 19, ez - 6, 38, 19, 8, all_faces(LID))]})
    bones.append({"name": "seam_left", "parent": "body", "pivot": [-21, ey, ez - 7],
                  "cubes": [cube(-21, ey - 21, ez - 9, 21, 42, 3, all_faces(SEAM))]})
    bones.append({"name": "seam_right", "parent": "body", "pivot": [21, ey, ez - 7],
                  "cubes": [cube(0, ey - 21, ez - 9, 21, 42, 3, all_faces(SEAM))]})

    for limb, name in enumerate(NAMES):
        ax, ay, az = anchor(limb)
        thick = [26, 24, 22, 20, 18, 16, 14, 12] if limb < 6 else [20, 19, 18, 17, 16, 15, 14, 14]
        for k in range(SEGMENTS):
            w = thick[k]
            top = ay - k * SEG
            bones.append({"name": "%s_seg%d" % (name, k + 1), "pivot": [round(ax, 2), round(top, 2), round(az, 2)],
                          "cubes": [cube(ax - w / 2, top - SEG - 1, az - w / 2, w, SEG + 2, w,
                                         all_faces(RED if name == "hand" and k >= 5 else TENT, up=TENT_END, down=TENT_END))]})
        tx, ty, tz = round(ax, 2), round(ay - SEGMENTS * SEG, 2), round(az, 2)
        if name == "face":
            bones.append({"name": "face", "pivot": [tx, ty, tz],
                          "cubes": [cube(tx - 20, ty - 20, tz - 18, 40, 46, 36, all_faces(STONE, north=FACE))]})
            bones.append({"name": "face_jaw", "parent": "face", "pivot": [tx, ty - 20, tz],
                          "cubes": [cube(tx - 16, ty - 30, tz - 17, 32, 12, 30, all_faces(STONE))]})
        elif name == "hand":
            bones.append({"name": "hand", "pivot": [tx, ty, tz],
                          "cubes": [cube(tx - 14, ty - 14, tz - 12, 28, 16, 24, all_faces(RED))]})
            for i, (fx, fz) in enumerate(((-10, -8), (0, -9), (10, -8), (-6, 9), (6, 9))):
                bones.append({"name": "hand_finger%d" % (i + 1), "parent": "hand", "pivot": [tx + fx, ty - 12, tz + fz],
                              "cubes": [cube(tx + fx - 3, ty - 38, tz + fz - 3, 6, 26, 6, all_faces(RED)),
                                        cube(tx + fx - 2, ty - 47, tz + fz - 2, 4, 10, 4, all_faces(CLAW))]})
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.patchwork_abomination", "texture_width": TEX, "texture_height": TEX,
                            "visible_bounds_width": 40, "visible_bounds_height": 30, "visible_bounds_offset": [0, 8, 0]},
            "bones": bones}]}


def animations():
    one = [1, 1, 1]
    lids_open = [1, 0.1, 1]
    flap_open = [0.04, 1, 1]
    curl = {"hand_finger1": [35, 0, 0], "hand_finger2": [35, 0, 0], "hand_finger3": [35, 0, 0],
            "hand_finger4": [-35, 0, 0], "hand_finger5": [-35, 0, 0]}
    return {"format_version": "1.8.0", "animations": {
        "idle": {"loop": True, "animation_length": 4,
                 "bones": {"body": {"scale": {"0.0": one, "2.0": [1.02, 0.985, 1.02], "4.0": one}}}},
        "crawl": {"loop": True, "animation_length": 1.2,
                  "bones": {"body": {"scale": {"0.0": one, "0.6": [1.06, 0.9, 1.06], "1.2": one}}}},
        "death": {"animation_length": 5,
                  "bones": {"body": {"scale": {"0.0": one, "0.5": [1.08, 0.94, 1.08], "4.0": [1.15, 0.55, 1.15], "5.0": [0.2, 0.1, 0.2]}}}},
        "seam_shut": {"animation_length": 0.1, "bones": {"seam_left": {"scale": one}, "seam_right": {"scale": one}}},
        "seam_open": {"animation_length": 1.5,
                      "bones": {"seam_left": {"scale": {"0.0": one, "1.5": flap_open}},
                                "seam_right": {"scale": {"0.0": one, "1.5": flap_open}}}},
        "eye_shut": {"animation_length": 0.3,
                     "bones": {"eyelid_upper": {"scale": {"0.0": lids_open, "0.3": one}},
                               "eyelid_lower": {"scale": {"0.0": lids_open, "0.3": one}}}},
        "eye_open": {"animation_length": 0.4,
                     "bones": {"eyelid_upper": {"scale": {"0.0": one, "0.4": lids_open}},
                               "eyelid_lower": {"scale": {"0.0": one, "0.4": lids_open}}}},
        "face_idle": {"loop": True, "animation_length": 3,
                      "bones": {"face_jaw": {"position": {"0.0": [0, 0, 0], "1.5": [0, -1, 0], "3.0": [0, 0, 0]}}}},
        "face_scream": {"animation_length": 1.6,
                        "bones": {"face_jaw": {"position": {"0.0": [0, 0, 0], "0.9": [0, -9, 0], "1.3": [0, -9, 0], "1.6": [0, 0, 0]}}}},
        "hand_open": {"animation_length": 0.3,
                      "bones": {name: {"rotation": {"0.0": angle, "0.3": [0, 0, 0]}} for name, angle in curl.items()}},
        "hand_grab": {"animation_length": 0.25,
                      "bones": {name: {"rotation": {"0.0": [0, 0, 0], "0.25": angle}} for name, angle in curl.items()}},
    }}


def noise(img, region, base, spread, cell, rng, darken_down=0.0):
    u, v, w, h = region
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            d = rng.randint(-spread, spread)
            shade = 1 - darken_down * (y / h)
            colour = tuple(max(0, min(255, int((c + d) * shade))) for c in base) + (255,)
            for yy in range(y, min(y + cell, h)):
                for xx in range(x, min(x + cell, w)):
                    img.putpixel((u + xx, v + yy), colour)


def stitches(draw, region, rng, count):
    u, v, w, h = region
    thread = (74, 34, 44, 255)
    for _ in range(count):
        x, y = rng.randint(4, w - 4), rng.randint(4, h - 4)
        angle = rng.uniform(0, math.pi)
        length = rng.randint(w // 3, w)
        dx, dy = math.cos(angle), math.sin(angle)
        for step in range(0, length, 1):
            px, py = x + dx * (step - length / 2), y + dy * (step - length / 2)
            if 1 <= px < w - 1 and 1 <= py < h - 1:
                draw.point((u + px, v + py), fill=(52, 30, 42, 255))
                if step % 5 == 0:
                    for t in (-2, -1, 1, 2):
                        qx, qy = px - dy * t, py + dx * t
                        if 0 <= qx < w and 0 <= qy < h:
                            draw.point((u + qx, v + qy), fill=thread)


def textures():
    rng = random.Random(11)
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    glow = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    draw, gdraw = ImageDraw.Draw(img), ImageDraw.Draw(glow)
    skin = (126, 108, 132)
    noise(img, SKIN_A, skin, 12, 3, rng, 0.3)
    noise(img, SKIN_B, skin, 12, 3, rng, 0.3)
    noise(img, SKIN_TOP, (118, 100, 126), 10, 3, rng)
    for region, count in ((SKIN_A, 5), (SKIN_B, 5), (SKIN_TOP, 3)):
        stitches(draw, region, rng, count)
    noise(img, TENT, (116, 98, 122), 10, 2, rng, 0.15)
    noise(img, TENT_END, (96, 80, 104), 8, 2, rng)
    noise(img, LID, (112, 92, 116), 8, 2, rng)
    noise(img, SEAM, (120, 100, 124), 8, 2, rng)
    stitches(draw, SEAM, rng, 3)
    noise(img, STONE, (150, 148, 152), 9, 2, rng, 0.15)
    noise(img, FACE, (158, 156, 160), 7, 2, rng, 0.1)
    noise(img, RED, (134, 62, 70), 12, 2, rng, 0.2)
    noise(img, CLAW, (58, 40, 48), 6, 2, rng)
    white = (238, 230, 255, 255)
    # The main eye: a dark socket, a pale iris, a slit pupil.
    u, v, w, h = EYE
    draw.rectangle([u, v, u + w - 1, v + h - 1], fill=(40, 26, 44, 255))
    for target in (draw, gdraw):
        target.ellipse([u + 3, v + 3, u + w - 4, v + h - 4], fill=white)
    draw.ellipse([u + 8, v + 8, u + w - 9, v + h - 9], fill=(120, 80, 170, 255))
    draw.rectangle([u + 11, v + 6, u + 12, v + h - 7], fill=(16, 8, 22, 255))
    # Slits.
    u, v, w, h = SLIT
    for target in (draw, gdraw):
        target.rectangle([u, v, u + w - 1, v + h - 1], fill=white)
    # The stone face: brow, glowing eyes, nose, mouth.
    u, v, w, h = FACE
    draw.rectangle([u + 3, v + 8, u + 20, v + 9], fill=(110, 108, 114, 255))
    for target in (draw, gdraw):
        target.rectangle([u + 4, v + 11, u + 9, v + 12], fill=white)
        target.rectangle([u + 14, v + 11, u + 19, v + 12], fill=white)
    draw.rectangle([u + 11, v + 12, u + 12, v + 19], fill=(120, 118, 124, 255))
    draw.rectangle([u + 8, v + 23, u + 15, v + 23], fill=(84, 82, 90, 255))
    out = os.path.join(ASSETS, "textures", "entity")
    os.makedirs(out, exist_ok=True)
    img.save(os.path.join(out, "patchwork_abomination.png"))
    glow.save(os.path.join(out, "patchwork_abomination_glowmask.png"))

    # The Heart of the Pale.
    heart = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    shape = ["................", "..XXX....XXX....", ".XXXXX..XXXXX...", ".XXXXXXXXXXXXX..", ".XXXXXXXXXXXXX..",
             ".XXXXXXXXXXXXX..", "..XXXXXXXXXXX...", "...XXXXXXXXX....", "....XXXXXXX.....", ".....XXXXX......",
             "......XXX.......", ".......X........", "................", "................", "................", "................"]
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch == "X":
                edge = any(shape[y + dy][x + dx] != "X" for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                d = rng.randint(-8, 8)
                base = (70, 52, 84) if edge else (176, 166, 190)
                heart.putpixel((x + 1, y + 2), tuple(max(0, min(255, c + d)) for c in base) + (255,))
    hd = ImageDraw.Draw(heart)
    hd.line([(8, 5), (7, 8), (9, 10), (8, 13)], fill=(74, 34, 44, 255))
    for y in (6, 9, 12):
        hd.line([(6, y), (10, y)], fill=(74, 34, 44, 255))
    hd.point((5, 6), fill=(238, 230, 255, 255))
    os.makedirs(os.path.join(ASSETS, "textures", "item"), exist_ok=True)
    heart.save(os.path.join(ASSETS, "textures", "item", "heart_of_the_pale.png"))


def write(path, data):
    path = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def main():
    write("geo/entity/patchwork_abomination.geo.json", geometry())
    write("animations/entity/patchwork_abomination.animation.json", animations())
    write("models/item/heart_of_the_pale.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "thepale:item/heart_of_the_pale"}})
    write("models/item/patchwork_abomination_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})
    textures()
    print("wrote the boss model into", os.path.normpath(ASSETS))


if __name__ == "__main__":
    main()
