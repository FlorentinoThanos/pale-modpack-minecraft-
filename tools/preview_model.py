#!/usr/bin/env python3
"""Draws a GeckoLib model to a PNG without the game, to check a generated model, its texture and its poses.

  python3 tools/preview_model.py frost_colossus out.png [--yaw 30] [--pitch 12] [--anim walk --time 1.5]
                                 [--var colossus_arm_left=0.5] [--pose pose.json] [--zoom 3]

Bones are moved the way GeckoLib moves them (x mirrored, a bone turns about z, then y, then x around its pivot), so a
pose that looks right here looks right in the game. --pose takes {"bone": {"rot": [x, y, z], "pos": [x, y, z]}} in
the game's own units (radians, pixels) and is applied on top of the animation, as the model class does.
Needs Pillow and numpy.
"""
import argparse
import json
import math
import os
import re

import numpy as np
from PIL import Image

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "thepale")


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1.0]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1.0]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1.0]])


def translate(x, y, z):
    m = np.identity(4)
    m[:3, 3] = (x, y, z)
    return m


def molang(expr, variables):
    if isinstance(expr, (int, float)):
        return float(expr)
    text = expr
    for name, value in variables.items():
        text = text.replace("query." + name, "(%r)" % value)
    text = re.sub(r"query\.[a-z_]+", "0", text)
    text = text.replace("math.sin", "_sin").replace("math.cos", "_cos")
    return float(eval(text, {"_sin": lambda d: math.sin(math.radians(d)), "_cos": lambda d: math.cos(math.radians(d))}))


def channel(data, time, variables):
    """A channel of an animation (a vector, or keyframes) at a time."""
    if isinstance(data, list):
        return [molang(v, variables) for v in data]
    frames = sorted((float(t), v["vector"] if isinstance(v, dict) else v) for t, v in data.items())
    if time <= frames[0][0]:
        return [molang(v, variables) for v in frames[0][1]]
    for (t0, v0), (t1, v1) in zip(frames, frames[1:]):
        if time <= t1:
            k = (time - t0) / (t1 - t0)
            k = k * k * (3 - 2 * k)
            return [molang(a, variables) * (1 - k) + molang(b, variables) * k for a, b in zip(v0, v1)]
    return [molang(v, variables) for v in frames[-1][1]]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("name")
    parser.add_argument("out")
    parser.add_argument("--yaw", type=float, default=28)
    parser.add_argument("--pitch", type=float, default=10)
    parser.add_argument("--zoom", type=float, default=3)
    parser.add_argument("--anim", action="append", default=[])
    parser.add_argument("--time", type=float, default=0)
    parser.add_argument("--var", action="append", default=[])
    parser.add_argument("--pose")
    parser.add_argument("--hide", action="append", default=[])
    args = parser.parse_args()

    geo = json.load(open(os.path.join(ASSETS, "geo", "entity", args.name + ".geo.json")))["minecraft:geometry"][0]
    texture = np.asarray(Image.open(os.path.join(ASSETS, "textures", "entity", args.name + ".png")).convert("RGBA")).astype(float)
    variables = {"anim_time": args.time}
    for item in args.var:
        key, value = item.split("=")
        variables[key] = float(value)

    # What each bone is turned and moved by, in the game's units.
    rot = {b["name"]: [0.0, 0.0, 0.0] for b in geo["bones"]}
    pos = {b["name"]: [0.0, 0.0, 0.0] for b in geo["bones"]}
    if args.anim:
        animations = json.load(open(os.path.join(ASSETS, "animations", "entity", args.name + ".animation.json")))["animations"]
        for name in args.anim:
            for bone, channels in animations[name]["bones"].items():
                if "rotation" in channels:
                    x, y, z = channel(channels["rotation"], args.time, variables)
                    rot[bone] = [math.radians(-x), math.radians(-y), math.radians(z)]
                if "position" in channels:
                    pos[bone] = channel(channels["position"], args.time, variables)
    if args.pose:
        for bone, values in json.load(open(args.pose)).items():
            if "rot" in values:
                rot[bone] = values["rot"]
            if "pos" in values:
                pos[bone] = values["pos"]

    by_name = {b["name"]: b for b in geo["bones"]}
    matrices = {}

    def matrix(name):
        if name not in matrices:
            b = by_name[name]
            px, py, pz = -b["pivot"][0], b["pivot"][1], b["pivot"][2]
            own = (translate(-pos[name][0], pos[name][1], pos[name][2]) @ translate(px, py, pz)
                   @ rot_z(rot[name][2]) @ rot_y(rot[name][1]) @ rot_x(rot[name][0]) @ translate(-px, -py, -pz))
            matrices[name] = matrix(b["parent"]) @ own if "parent" in b else own
        return matrices[name]

    # The camera: turned about the model, looking a little down, the model's front toward the viewer at yaw 0.
    view = rot_x(math.radians(args.pitch)) @ rot_y(math.radians(args.yaw + 180))
    light = np.array([0.35, 0.8, -0.5])
    light /= np.linalg.norm(light)

    faces = []
    for b in geo["bones"]:
        if b["name"] in args.hide or any(b["name"].startswith(h) for h in args.hide):
            continue
        m = view @ matrix(b["name"])
        for c in b.get("cubes", []):
            x0, y0, z0 = c["origin"]
            w, h, d = c["size"]
            # Corners in the game's space: x mirrored.
            a, bx = -(x0 + w), -x0
            corners = {
                # name: (top left, top right, bottom left) as seen from outside, and the normal
                "north": ((bx, y0 + h, z0), (a, y0 + h, z0), (bx, y0, z0), (0, 0, -1)),
                "south": ((a, y0 + h, z0 + d), (bx, y0 + h, z0 + d), (a, y0, z0 + d), (0, 0, 1)),
                "west": ((a, y0 + h, z0), (a, y0 + h, z0 + d), (a, y0, z0), (-1, 0, 0)),
                "east": ((bx, y0 + h, z0 + d), (bx, y0 + h, z0), (bx, y0, z0 + d), (1, 0, 0)),
                "up": ((a, y0 + h, z0 + d), (bx, y0 + h, z0 + d), (a, y0 + h, z0), (0, 1, 0)),
                "down": ((a, y0, z0), (bx, y0, z0), (a, y0, z0 + d), (0, -1, 0)),
            }
            for name, (tl, tr, bl, normal) in corners.items():
                uv = c["uv"].get(name)
                if uv is None:
                    continue
                points = [m @ np.array([*p, 1.0]) for p in (tl, tr, bl)]
                n = (m[:3, :3] @ np.array(normal))
                n_world = (matrix(b["name"])[:3, :3] @ np.array(normal))
                if n[2] >= 0:
                    continue  # facing away (the camera looks along -z here)
                shade = 0.62 + 0.38 * max(0.0, float(n_world @ light))
                faces.append((points, uv, shade))

    all_points = np.array([p[:3] for f in faces for p in f[0]])
    lo, hi = all_points.min(axis=0), all_points.max(axis=0)
    zoom = args.zoom
    width = int((hi[0] - lo[0]) * zoom) + 40
    height = int((hi[1] - lo[1]) * zoom) + 40
    colour = np.zeros((height, width, 3))
    colour[:] = (150, 170, 200)
    depth = np.full((height, width), np.inf)

    def screen(p):
        return np.array([(p[0] - lo[0]) * zoom + 20, (hi[1] - p[1]) * zoom + 20, p[2]])

    # Solid faces first, then the see-through ones, back to front.
    def solid(face):
        u, v = face[1]["uv"]
        w, h = face[1]["uv_size"]
        patch = texture[int(v):int(v + max(h, 1)), int(u):int(u + max(w, 1)), 3]
        return patch.size > 0 and patch.min() >= 250
    ordered = [f for f in faces if solid(f)] + sorted((f for f in faces if not solid(f)), key=lambda f: -min(p[2] for p in f[0]))
    for points, uv, shade in ordered:
        p0, p1, p2 = (screen(p) for p in points)
        e1, e2 = p1 - p0, p2 - p0
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            continue
        p3 = p1 + e2
        xs = [p0[0], p1[0], p2[0], p3[0]]
        ys = [p0[1], p1[1], p2[1], p3[1]]
        x_lo, x_hi = max(int(min(xs)), 0), min(int(max(xs)) + 1, width - 1)
        y_lo, y_hi = max(int(min(ys)), 0), min(int(max(ys)) + 1, height - 1)
        if x_lo > x_hi or y_lo > y_hi:
            continue
        gx, gy = np.meshgrid(np.arange(x_lo, x_hi + 1) + 0.5, np.arange(y_lo, y_hi + 1) + 0.5)
        dx, dy = gx - p0[0], gy - p0[1]
        s = (dx * e2[1] - dy * e2[0]) / det
        t = (dy * e1[0] - dx * e1[1]) / det
        z = p0[2] + s * e1[2] + t * e2[2]
        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        tu = np.clip((uv["uv"][0] + s * uv["uv_size"][0]).astype(int), 0, texture.shape[1] - 1)
        tv = np.clip((uv["uv"][1] + t * uv["uv_size"][1]).astype(int), 0, texture.shape[0] - 1)
        texel = texture[tv, tu]
        alpha = texel[..., 3] / 255.0
        window = depth[y_lo:y_hi + 1, x_lo:x_hi + 1]
        visible = inside & (z > -np.inf) & (-z < window) & (alpha > 0.02)
        # Nearer is a larger z here (the camera looks along -z), so depth holds -z.
        blended = colour[y_lo:y_hi + 1, x_lo:x_hi + 1] * (1 - alpha[..., None]) + texel[..., :3] * shade * alpha[..., None]
        colour[y_lo:y_hi + 1, x_lo:x_hi + 1] = np.where(visible[..., None], blended, colour[y_lo:y_hi + 1, x_lo:x_hi + 1])
        depth[y_lo:y_hi + 1, x_lo:x_hi + 1] = np.where(visible & (alpha > 0.9), -z, window)

    Image.fromarray(np.clip(colour, 0, 255).astype(np.uint8)).save(args.out)
    print("wrote", args.out, "%dx%d" % (width, height))


if __name__ == "__main__":
    main()
