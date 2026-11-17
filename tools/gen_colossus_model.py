#!/usr/bin/env python3
"""Builds the Frost Colossus: geometry, animations and textures (python3 tools/gen_colossus_model.py, needs Pillow).

The model is drawn at half size, 16 pixels to a block, and the renderer doubles it (ColossusPose.SCALE): about 236
pixels tall, so close to 30 blocks in the game, with one pixel an eighth of a block. Coordinates here are the file's:
x toward the colossus's left, y up, z toward its back (its front is -z).

The joints (pivots, limb lengths, the seats) must match the numbers in com.example.thepale.colossus.ColossusPose: the
server works out the hitboxes, the seats and the hand from them, and the client moves the same bones.

Who moves which bone:
  code (ColossusPose)  body (it sinks and leans with the steps, and bows), head (looking), left_leg/right_leg (each
                       leg reaches from its hip to where its foot stands), and an arm while its hand is lifting or
                       lowering somebody
  animations           everything else: root and torso only in sit_down/sleep/stand_up, head_tilt, the arms, the
                       hands and fingers, the legs' fold when sitting
The walk animation swings each arm by query.colossus_arm_left / query.colossus_arm_right (-1..1, forward with the
opposite foot) and takes how fast it walks from query.colossus_stride (0..1); the model class sets all three.
"""
import json
import math
import os
import random

from PIL import Image

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "thepale")
NAME = "frost_colossus"
TEX = 256

# ----- The skeleton (must match ColossusPose) -----
HIP_Y = 84                    # the body turns about (0, HIP_Y, 0); a leg is this long from hip to sole
HIP_X = 21
WAIST = (0, 96, 0)
HEAD = (0, 192, -6)
SHOULDER = (64, 178, 0)       # left; the right side is mirrored
ELBOW = (64, 124, 0)
WRIST = (64, 62, 0)
KNUCKLES = (64, 40, 0)
SEAT = (46, 200, -10)          # on top of the snow, toward the front edge
SIT_DROP = 62                 # how far the hips come down to sit

# ----- The texture's regions: (u, v, width, height) -----
STONE = (0, 0, 128, 128)
STONE_TOP = (128, 0, 128, 64)
SNOW = (128, 64, 64, 64)
ICE = (192, 64, 64, 64)
FROST = (0, 128, 128, 64)
DARK = (128, 128, 64, 64)
SNOW_SIDE = (192, 128, 64, 5)
ICICLE = (192, 144, 8, 32)
EYE = (200, 144, 16, 4)
SOCKET = (216, 144, 32, 8)
# The caches it carries on a walk: chests of dark wood bound with iron, a gold latch that glints.
CHEST_SIDE = (0, 192, 32, 16)
CHEST_TOP = (32, 192, 32, 16)
LATCH = (64, 192, 4, 4)
CRUST = (72, 192, 32, 16)

rng = random.Random(30)


def pick(region, w, h, step_u=8, step_v=4):
    """A w x h window somewhere in a region, on the brick grid. Larger than the region: the whole region, stretched."""
    u, v, rw, rh = region
    w, h = max(w, 1), max(h, 1)
    if w > rw or h > rh:
        return {"uv": [u, v], "uv_size": [min(w, rw), min(h, rh)]}
    du = rng.randrange(0, int(rw - w) // step_u + 1) * step_u
    dv = rng.randrange(0, int(rh - h) // step_v + 1) * step_v
    return {"uv": [u + du, v + dv], "uv_size": [w, h]}


def cube(x0, y0, z0, w, h, d, side=STONE, up=None, down=DARK, anchor_top=False):
    """A box with every face cut from the texture at its own size, so a pixel is the same size everywhere."""
    if up is None:
        up = {id(STONE): STONE_TOP, id(FROST): STONE_TOP, id(DARK): DARK}.get(id(side), side)
    if side is SNOW_SIDE or side is ICICLE:
        # The whole strip, top edge up: its lower edge is ragged (snow) or fades out (an icicle).
        u, v, rw, rh = side
        ns = {"uv": [u, v], "uv_size": [min(w, rw), rh]}
        ew = {"uv": [u, v], "uv_size": [min(d, rw), rh]}
        faces = {"north": ns, "south": dict(ns), "east": ew, "west": dict(ew)}
    elif anchor_top:
        # Frosted stone is whitest at the top of its region: keep the top of the face there.
        u, v, rw, rh = side
        faces = {}
        for name, fw in (("north", w), ("south", w), ("east", d), ("west", d)):
            du = rng.randrange(0, max(int(rw - fw) // 8, 0) + 1) * 8
            faces[name] = {"uv": [u + du, v], "uv_size": [min(fw, rw), min(h, rh)]}
    else:
        faces = {"north": pick(side, w, h), "south": pick(side, w, h), "east": pick(side, d, h), "west": pick(side, d, h)}
    faces["up"] = pick(up, w, d, 8, 8)
    faces["down"] = pick(down, w, d, 8, 8)
    return {"origin": [x0, y0, z0], "size": [w, h, d], "uv": faces}


def mirrored(c):
    """The same box on the other side of the body, with its own cut of the texture."""
    x0, y0, z0 = c["origin"]
    w, h, d = c["size"]
    out = {"origin": [-x0 - w, y0, z0], "size": [w, h, d], "uv": {}}
    for name, face in c["uv"].items():
        out["uv"][name] = dict(face)
    return out


def snow(x0, y0, z0, w, d, thick=4):
    """A cap of snow: a slab, and a ragged skirt hanging a little over the edge."""
    return [cube(x0, y0, z0, w, thick, d, side=SNOW, up=SNOW, down=SNOW),
            cube(x0 - 1, y0 - 3, z0 - 1, w + 2, 5, d + 2, side=SNOW_SIDE, up=SNOW, down=SNOW)]


def icicle(x, top, z, length, thick=4):
    """Hangs from (x, top, z): a thick upper half and a thin tip."""
    upper = length // 2
    return [cube(x - thick / 2, top - upper, z - thick / 2, thick, upper, thick, side=ICE, up=ICE, down=ICE),
            cube(x - thick / 4, top - length, z - thick / 4, thick / 2, length - upper, thick / 2, side=ICICLE, up=ICE, down=ICE)]


def fixed(x0, y0, z0, w, h, d, side, up, down=None, inflate=0):
    """A box whose faces are cut from the top left of their regions, with no randomness."""
    def at(region, fw, fh):
        return {"uv": [region[0], region[1]], "uv_size": [min(fw, region[2]), min(fh, region[3])]}
    faces = {"north": at(side, w, h), "south": at(side, w, h), "east": at(side, d, h), "west": at(side, d, h),
             "up": at(up, w, d), "down": at(down or up, w, d)}
    out = {"origin": [x0, y0, z0], "size": [w, h, d], "uv": faces}
    if inflate:
        out["inflate"] = inflate
    return out


def chest(x0, y0, z0, w, h, d):
    """A chest, frozen over: the box, its lid's rim, the latch on the front, a crust of ice over one corner."""
    return [fixed(x0, y0, z0, w, h, d, CHEST_SIDE, CHEST_TOP),
            fixed(x0 - 0.5, y0 + h - 4, z0 - 0.5, w + 1, 1, d + 1, DARK, DARK),
            fixed(x0 + w / 2 - 1, y0 + h - 6, z0 - 1, 2, 3, 1, LATCH, LATCH),
            fixed(x0 - 1, y0 - 1, z0 + d / 2, w / 2 + 1, h / 2 + 2, d / 2 + 1, CRUST, CRUST)]


def bone(name, parent, pivot, cubes=()):
    out = {"name": name, "pivot": list(pivot)}
    if parent:
        out["parent"] = parent
    if cubes:
        out["cubes"] = list(cubes)
    return out


def side_bones(name, parent, pivot, cubes, left):
    """A bone of the left side as given, or mirrored onto the right."""
    if left:
        return bone("left_" + name if not name.startswith(("snow_", "ice_")) else name.replace("_", "_left_", 1), parent, pivot, cubes)
    pivot = (-pivot[0], pivot[1], pivot[2])
    cubes = [mirrored(c) for c in cubes]
    return bone("right_" + name if not name.startswith(("snow_", "ice_")) else name.replace("_", "_right_", 1), parent, pivot, cubes)


def geometry():
    bones = [bone("root", None, (0, 0, 0))]

    # The pelvis stays with the body bone; everything above the waist hangs on the torso.
    bones.append(bone("body", "root", (0, HIP_Y, 0), [
        cube(-34, 70, -22, 68, 28, 44),
        cube(-36, 84, -24, 72, 8, 48, side=DARK),          # a belt of darker stone
    ]))
    torso = [
        cube(-30, 94, -20, 60, 32, 40),                    # the belly
        cube(-38, 122, -24, 76, 68, 48),                   # the chest
        cube(-40, 140, -26, 80, 10, 52, side=DARK),        # a band across the chest
        cube(30, 176, -22, 32, 20, 44, side=FROST, anchor_top=True),   # the shoulder shelves: the seats
        cube(-62, 176, -22, 32, 20, 44, side=FROST, anchor_top=True),
        cube(-22, 188, 2, 44, 28, 24, side=FROST, anchor_top=True),    # the hump behind the head
        cube(-18, 216, 8, 12, 14, 12),                     # and the rocks standing on it
        cube(4, 216, 6, 12, 22, 12),
        cube(-6, 216, 16, 12, 8, 10),
    ]
    bones.append(bone("torso", "body", WAIST, torso))
    bones.append(bone("seat_left", "torso", SEAT))
    bones.append(bone("seat_right", "torso", (-SEAT[0], SEAT[1], SEAT[2])))
    bones.append(bone("snow_shoulders", "torso", (0, 196, 0),
                      snow(29, 196, -23, 34, 46) + snow(-63, 196, -23, 34, 46, thick=6)))
    bones.append(bone("snow_back", "torso", (0, 216, 14),
                      snow(-23, 216, 1, 46, 26, thick=3) + snow(-19, 230, 7, 14, 14, thick=3)
                      + snow(3, 238, 5, 14, 14, thick=3) + snow(-7, 224, 15, 14, 12, thick=2)))
    bones.append(bone("ice_chest", "torso", (20, 168, -26),
                      [cube(6, 148, -28, 32, 40, 4, side=ICE, up=ICE, down=ICE),
                       cube(-34, 100, -23, 22, 20, 3, side=ICE, up=ICE, down=ICE)]
                      + icicle(12, 148, -26, 16) + icicle(22, 148, -26, 24, 6) + icicle(32, 148, -26, 12)
                      + icicle(44, 176, -18, 18) + icicle(56, 176, -14, 12) + icicle(-40, 176, -18, 14)
                      + icicle(-54, 176, -10, 22, 6) + icicle(-28, 100, -21, 12)))

    # The head: small, sunk between the shoulders. "head" looks around (code), "head_tilt" is the animations'.
    bones.append(bone("head", "torso", HEAD))
    bones.append(bone("head_tilt", "head", HEAD, [
        cube(-15, 190, -22, 30, 24, 28),
        cube(-17, 204, -25, 34, 6, 6, side=DARK),                         # the brow
        cube(-14, 197, -22.75, 28, 6, 1, side=SOCKET, up=DARK),          # the shadow the eyes sit in
        cube(-10, 190, -24, 20, 5, 4),                                    # the chin
    ]))
    bones.append(bone("eyes", "head_tilt", (0, 200, -23), [
        cube(3, 198.5, -23.5, 9, 3, 1, side=EYE, up=EYE, down=EYE),
        cube(-12, 198.5, -23.5, 9, 3, 1, side=EYE, up=EYE, down=EYE),
    ]))
    bones.append(bone("snow_head", "head_tilt", (0, 214, -8), snow(-16, 214, -23, 32, 30, thick=3)))

    for left in (True, False):
        s = "left" if left else "right"
        bones.append(side_bones("arm", "torso", SHOULDER, [
            cube(50, 122, -15, 28, 64, 30),
            cube(56, 166, -19, 28, 26, 38, side=FROST, anchor_top=True),  # the shoulder block
        ], left))
        bones.append(side_bones("snow_arm", s + "_arm", (70, 192, 0), snow(55, 192, -20, 30, 40, thick=4 if left else 6), left))
        bones.append(side_bones("forearm", s + "_arm", ELBOW, [
            cube(46, 60, -18, 36, 68, 36),
            cube(44, 84, -20, 40, 12, 40, side=DARK),                    # a band
        ], left))
        bones.append(side_bones("snow_forearm", s + "_forearm", (64, 128, 0), snow(45, 128, -19, 38, 38, thick=3), left))
        plates = [cube(82, 66, -14, 4, 52, 28, side=ICE, up=ICE, down=ICE)]
        hanging = icicle(80, 84, -16, 16) + icicle(80, 84, 12, 22, 6) + icicle(70, 60, 16, 12)
        if left:
            plates.append(cube(52, 98, -21, 26, 26, 3, side=ICE, up=ICE, down=ICE))
            hanging += icicle(58, 98, -20, 14)
        else:
            hanging += icicle(52, 60, 16, 18, 6) + icicle(60, 84, -20, 14)
        bones.append(side_bones("ice_forearm", s + "_forearm", (82, 92, 0), plates + hanging, left))
        # The hand: its palm faces the body. Fingers hang straight here; the animations curl them.
        bones.append(side_bones("hand", s + "_forearm", WRIST, [
            cube(55, 40, -20, 18, 26, 40),
            cube(50, 38, -28, 12, 18, 8),                                # the thumb
        ], left))
        bones.append(side_bones("fingers", s + "_hand", KNUCKLES, [
            cube(56, 16, -20, 16, 24, 12),
            cube(56, 14, -6, 16, 26, 12),
            cube(56, 18, 8, 16, 22, 12),
        ], left))
        bones.append(side_bones("leg", "root", (HIP_X, HIP_Y, 0), [
            cube(8, 12, -15, 26, 78, 30),
            cube(9, 40, -19, 24, 16, 4, side=DARK),                      # the knee
            cube(5, 0, -24, 32, 14, 40, side=FROST, anchor_top=True),    # the foot
        ], left))
        bones.append(side_bones("snow_foot", s + "_leg", (21, 14, -4), snow(4, 14, -25, 34, 42, thick=3), left))

    # The caches it carries on a walk (shown while they hold something). Drawn last and cut from fixed places in the
    # texture, so nothing above changes when they do.
    for name, (x0, y0, z0, w, h, d) in (("cache_left", (39, 199, 2, 14, 10, 12)),
                                        ("cache_right", (-53, 201, 2, 14, 10, 12)),
                                        ("cache_back", (-6, 219, 2, 12, 11, 12))):
        bones.append(bone(name, "torso", (x0 + w / 2, y0, z0 + d / 2), chest(x0, y0, z0, w, h, d)))

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry." + NAME, "texture_width": TEX, "texture_height": TEX,
                            "visible_bounds_width": 24, "visible_bounds_height": 34, "visible_bounds_offset": [0, 16, 0]},
            "bones": bones}]}


# ----- Animations -----
# Rotations below are written the way the game applies them (degrees about the model's right, up and back axes, a
# positive turn about the right axis swings a hanging limb forward); rt() turns them into the file's convention.

def rt(x, y, z):
    return [neg(x), neg(y), z]


def neg(v):
    if isinstance(v, str):
        return "-(" + v + ")"
    return -v if v else 0


def keys(frames, easing="easeInOutSine"):
    """{time: rotation or position} with easing between the keyframes."""
    out = {}
    for i, (t, value) in enumerate(frames):
        out["%.2f" % t if t != int(t) else "%.1f" % t] = value if i == 0 else {"vector": value, "easing": easing}
    return out


CURL = 32          # how far the fingers hang curled
OUT = 4            # the arms hang this far out from the body
BEND = 9           # and the elbows are bent this much


def arm_rest(side_sign, swing="0", fore="0", curl=CURL):
    """Both arms' bones for one side: side_sign is -1 for the left arm (it hangs out toward the model's left)."""
    s = "left" if side_sign < 0 else "right"
    return {
        s + "_arm": {"rotation": rt(swing, 0, side_sign * OUT)},
        s + "_forearm": {"rotation": rt(fore, 0, 0)},
        s + "_fingers": {"rotation": rt(0, 0, -side_sign * curl)},
    }


def animations():
    t = "query.anim_time"
    al = "query.colossus_arm_left"
    ar = "query.colossus_arm_right"
    a = "query.colossus_stride"
    idle = {}
    idle.update(arm_rest(-1, "math.sin(%s*42)*1.6" % t, "%d+math.sin(%s*42-50)*1.5" % (BEND, t)))
    idle.update(arm_rest(1, "math.sin(%s*42+40)*1.6" % t, "%d+math.sin(%s*42-10)*1.5" % (BEND, t)))
    idle["head_tilt"] = {"rotation": rt("math.sin(%s*42+90)*1.2" % t, 0, 0)}

    # Arms swing against the legs (each goes forward with the opposite foot), the elbows bending as they come forward.
    walk = {}
    walk.update(arm_rest(-1, "%s*15+math.sin(%s*42)*1.6*(1-%s)" % (al, t, a), "%d+(1+%s)*5*%s" % (BEND, al, a)))
    walk.update(arm_rest(1, "%s*15+math.sin(%s*42+40)*1.6*(1-%s)" % (ar, t, a), "%d+(1+%s)*5*%s" % (BEND, ar, a)))
    walk["head_tilt"] = {"rotation": rt("math.sin(%s*42+90)*1.2*(1-%s)-(%s*%s)*1.2" % (t, a, al, al), 0, 0)}

    look_around = {"head_tilt": {"rotation": keys([
        (0, rt(0, 0, 0)), (1.2, rt(4, 38, 0)), (2.4, rt(4, 38, 0)), (4.0, rt(2, -40, 0)), (5.2, rt(2, -40, 0)),
        (6.4, rt(0, 0, 0))])}}
    look_sky = {"head_tilt": {"rotation": keys([
        (0, rt(0, 0, 0)), (1.6, rt(34, 0, 0)), (5.0, rt(36, 6, 0)), (6.5, rt(0, 0, 0))])}}
    shake_head = {"head_tilt": {"rotation": keys([
        (0, rt(0, 0, 0)), (0.35, rt(0, 20, 0)), (0.9, rt(0, -20, 0)), (1.45, rt(0, 16, 0)), (1.9, rt(0, -10, 0)),
        (2.3, rt(0, 0, 0))])}}
    nod = {"head_tilt": {"rotation": keys([
        (0, rt(0, 0, 0)), (0.45, rt(-16, 0, 0)), (0.9, rt(3, 0, 0)), (1.3, rt(0, 0, 0))])}}

    # Sitting: the hips come down, the legs go out in front, the chest sinks forward over them, the arms come in and
    # the head drops. Under snow it reads as a hill.
    def sat(k):
        """The sitting pose, k of the way there (0 standing, 1 down)."""
        pose = {
            "root": {"position": [0, -SIT_DROP * k, 0]},
            "torso": {"rotation": rt(-34 * k, 0, 0)},
            "head_tilt": {"rotation": rt(-26 * k, 0, 0)},
            # The feet a little apart (a turn to the left takes a leg lying out in front to the left).
            "left_leg": {"rotation": rt(82 * k, 9 * k, 0)},
            "right_leg": {"rotation": rt(82 * k, -9 * k, 0)},
        }
        for sign, s in ((-1, "left"), (1, "right")):
            pose[s + "_arm"] = {"rotation": rt(38 * k, 0, sign * (OUT + 5 * k))}
            pose[s + "_forearm"] = {"rotation": rt(BEND + 46 * k, 0, -sign * 16 * k)}
            pose[s + "_fingers"] = {"rotation": rt(0, 0, -sign * (CURL + 20 * k))}
        return pose

    def sequence(steps):
        """[(time, k)] into keyframes of the sitting pose, bone by bone."""
        out = {}
        for time, k in steps:
            for name, channels in sat(k).items():
                for channel, value in channels.items():
                    out.setdefault(name, {}).setdefault(channel, []).append((time, value))
        return {name: {channel: keys(frames) for channel, frames in channels.items()} for name, channels in out.items()}

    sit_down = sequence([(0, 0), (1.2, 0.35), (3.0, 1)])
    stand_up = sequence([(0, 1), (0.8, 1.04), (2.2, 0.3), (3.0, 0)])
    sleep = sequence([(0, 1), (4, 1), (8, 1)])
    # Asleep it breathes: the chest and the head rise and sink a little.
    sleep["torso"]["rotation"] = keys([(0, rt(-34, 0, 0)), (4, rt(-32.5, 0, 0)), (8, rt(-34, 0, 0))])
    sleep["head_tilt"]["rotation"] = keys([(0, rt(-26, 0, 0)), (4, rt(-24.5, 0, 0)), (8, rt(-26, 0, 0))])

    def hand(s, sign, curl_to, curl_from=CURL, length=0.6):
        return {s + "_fingers": {"rotation": keys([(0, rt(0, 0, -sign * curl_from)), (length, rt(0, 0, -sign * curl_to))])}}

    return {"format_version": "1.8.0", "animations": {
        "idle": {"loop": True, "animation_length": 60 / 7.0, "bones": idle},
        "walk": {"loop": True, "animation_length": 60 / 7.0, "bones": walk},
        "look_around": {"animation_length": 6.4, "bones": look_around},
        "look_sky": {"animation_length": 6.5, "bones": look_sky},
        "shake_head": {"animation_length": 2.3, "bones": shake_head},
        "nod": {"animation_length": 1.3, "bones": nod},
        "sit_down": {"loop": "hold_on_last_frame", "animation_length": 3.0, "bones": sit_down},
        "sleep": {"loop": True, "animation_length": 8.0, "bones": sleep},
        "stand_up": {"animation_length": 3.0, "bones": stand_up},
        # The open hand, held out flat; and the same hand cupped around whoever sits in it.
        "offer_hand": {"loop": "hold_on_last_frame", "animation_length": 0.6, "bones": hand("right", 1, 4)},
        "offer_hand_left": {"loop": "hold_on_last_frame", "animation_length": 0.6, "bones": hand("left", -1, 4)},
        "cradle": {"loop": "hold_on_last_frame", "animation_length": 0.6, "bones": hand("right", 1, 50)},
        "cradle_left": {"loop": "hold_on_last_frame", "animation_length": 0.6, "bones": hand("left", -1, 50)},
        # And the fingers curl back to how they hang.
        "rest_hand": {"loop": "hold_on_last_frame", "animation_length": 0.8, "bones": hand("right", 1, CURL, 4, 0.8)},
        "rest_hand_left": {"loop": "hold_on_last_frame", "animation_length": 0.8, "bones": hand("left", -1, CURL, 4, 0.8)},
    }}


# ----- Textures -----

def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(colour, factor, add=0):
    return tuple(clamp(c * factor + add) for c in colour[:3]) + (colour[3] if len(colour) > 3 else 255,)


def bricks(img, region, base, frost=0.0, plain=False):
    """Courses of stone brick, each brick its own shade, with mortar, chipped corners and the odd crack."""
    u0, v0, w, h = region
    bw, bh = (8, 8) if plain else (8, 4)
    rime = [[rng.random() for _ in range(w // 2 + 1)] for _ in range(h // 2 + 1)]
    for row in range(h // bh):
        offset = (row % 2) * (bw // 2)
        for col in range(-1, w // bw + 1):
            tone = rng.uniform(0.84, 1.14)
            warm = rng.uniform(-5, 5)
            mossy = rng.random() < 0.06
            for y in range(bh):
                for x in range(bw):
                    px, py = col * bw + offset + x, row * bh + y
                    if not 0 <= px < w:
                        continue
                    factor = tone * rng.uniform(0.95, 1.05)
                    if y == bh - 1 or x == bw - 1:
                        factor *= 0.6                       # mortar
                    elif y == 0:
                        factor *= 1.09                      # the lit upper edge
                    colour = [base[0] * factor + warm, base[1] * factor, base[2] * factor - warm]
                    if mossy:
                        colour = [colour[0] * 0.9, colour[1] * 0.98, colour[2] * 1.06]
                    if frost:
                        # Rime: thick near the top of the region, thinning out below, in soft patches.
                        amount = frost * max(0.0, 1 - py / (h * 0.75))
                        patch = rime[(py // 2) % len(rime)][(px // 2) % len(rime[0])]
                        k = max(0.0, min(1.0, (amount * 1.5 + patch - 1.0) * 2.2))
                        colour = [colour[0] + (226 - colour[0]) * k, colour[1] + (234 - colour[1]) * k, colour[2] + (246 - colour[2]) * k]
                    img.putpixel((u0 + px, v0 + py), (clamp(colour[0]), clamp(colour[1]), clamp(colour[2]), 255))
    for _ in range(max(2, w * h // 1400)):                  # cracks
        x, y = rng.randrange(w), rng.randrange(h)
        for _ in range(rng.randrange(8, 26)):
            if 0 <= x < w and 0 <= y < h:
                old = img.getpixel((u0 + x, v0 + y))
                img.putpixel((u0 + x, v0 + y), shade(old, 0.5))
            x += rng.choice((-1, 0, 1))
            y += rng.choice((0, 1, 1))


def fill(img, region, colour_at):
    u0, v0, w, h = region
    for y in range(h):
        for x in range(w):
            img.putpixel((u0 + x, v0 + y), colour_at(x, y, w, h))


def textures():
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    glow = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    stone = (112, 100, 94)
    bricks(img, STONE, stone)
    bricks(img, STONE_TOP, (104, 94, 90), plain=True)
    bricks(img, FROST, stone, frost=0.85)
    bricks(img, DARK, (64, 58, 60), plain=True)

    # Snow: white, with soft blue hollows.
    hollows = [(rng.randrange(64), rng.randrange(64), rng.uniform(4, 10)) for _ in range(9)]

    def snow_at(x, y, w, h):
        dip = 0.0
        for hx, hy, hr in hollows:
            dx = min(abs(x - hx), w - abs(x - hx))
            dy = min(abs(y - hy), h - abs(y - hy))
            dip = max(dip, 1 - math.hypot(dx, dy) / hr)
        k = max(0.0, dip) * 0.5 + rng.uniform(0, 0.1)
        return (clamp(244 - 34 * k), clamp(248 - 22 * k), clamp(254 - 6 * k), 255)
    fill(img, SNOW, snow_at)

    # The skirt of a snow cap: solid along its top, hanging in uneven tongues.
    u0, v0, w, h = SNOW_SIDE
    reach = 3
    for x in range(w):
        reach = max(2, min(h, reach + rng.choice((-2, -1, -1, 0, 0, 1, 1, 2))))
        for y in range(reach):
            k = y / h
            img.putpixel((u0 + x, v0 + y), (clamp(242 - 34 * k), clamp(247 - 22 * k), 254, 255))

    # Ice: see-through blue, with pale streaks running down it and darker veins.
    streaks = {rng.randrange(64): rng.uniform(0.5, 1.0) for _ in range(12)}

    def ice_at(x, y, w, h):
        s = streaks.get(x, 0) * (0.6 + 0.4 * math.sin(y * 0.4 + x))
        k = rng.uniform(-0.05, 0.05) + s * 0.45
        vein = (x * 7 + y * 3) % 23 == 0 or (x * 5 - y * 9) % 37 == 0
        if vein:
            return (96, 150, 205, 205)
        return (clamp(150 + 80 * k), clamp(202 + 44 * k), clamp(238 + 14 * k), clamp(178 + 60 * s))
    fill(img, ICE, ice_at)
    fill(img, ICICLE, lambda x, y, w, h: (clamp(176 + 50 * y / h), clamp(220 + 26 * y / h), 250, clamp(214 - 70 * y / h)))

    # The eyes: slits of cold light. They are the whole of the glow mask.
    def eye_at(x, y, w, h):
        edge = y == 0 or y == h - 1 or x == 0 or x == w - 1
        return (150, 222, 255, 255) if edge else (226, 250, 255, 255)
    fill(img, EYE, eye_at)
    fill(glow, EYE, eye_at)
    fill(img, SOCKET, lambda x, y, w, h: (22 + rng.randrange(6), 22 + rng.randrange(6), 30 + rng.randrange(8), 255))

    # The chests: their own random numbers, so the rest of the texture stays as it was.
    crng = random.Random(77)

    def plank_at(x, y, w, h):
        if y in (0, h - 1) or x in (0, w - 1) or y == 5:
            return (62, 64, 72, 255)                                   # iron bands and the lid's seam
        if x % 8 == 0:
            return (58, 36, 22, 255)
        k = crng.uniform(-0.08, 0.08) + (0.06 if (y // 3) % 2 else 0)
        return (clamp(112 * (1 + k)), clamp(74 * (1 + k)), clamp(44 * (1 + k)), 255)
    fill(img, CHEST_SIDE, plank_at)

    def lid_at(x, y, w, h):
        if x in (0, w - 1) or y in (0, h - 1):
            return (62, 64, 72, 255)
        if y % 5 == 0:
            return (58, 36, 22, 255)
        k = crng.uniform(-0.08, 0.08)
        return (clamp(124 * (1 + k)), clamp(84 * (1 + k)), clamp(50 * (1 + k)), 255)
    fill(img, CHEST_TOP, lid_at)

    def latch_at(x, y, w, h):
        return (255, 226, 96, 255) if (x + y) % 3 else (255, 248, 190, 255)
    fill(img, LATCH, latch_at)
    fill(glow, LATCH, lambda x, y, w, h: (255, 226, 96, 150))

    def crust_at(x, y, w, h):
        k = crng.uniform(0, 0.25) + (0.3 if (x + 2 * y) % 11 == 0 else 0)
        return (clamp(170 + 70 * k), clamp(214 + 40 * k), 250, clamp(150 + 80 * k))
    fill(img, CRUST, crust_at)

    out = os.path.join(ASSETS, "textures", "entity")
    os.makedirs(out, exist_ok=True)
    img.save(os.path.join(out, NAME + ".png"))
    glow.save(os.path.join(out, NAME + "_glowmask.png"))


def write(path, data):
    path = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def main():
    write("geo/entity/%s.geo.json" % NAME, geometry())
    write("animations/entity/%s.animation.json" % NAME, animations())
    textures()
    print("wrote the colossus model into", os.path.normpath(ASSETS))


if __name__ == "__main__":
    main()
