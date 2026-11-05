#!/usr/bin/env python3
"""Checks the quests of gen_quests.py against the pack's jars: every item, mob, biome, structure, advancement and
enchantment a quest names must exist in some jar, and every item a quest asks for should be craftable (a recipe
output) or known to drop or generate.

    python3 modpack/tools/check_quests.py <minecraft-1.21.1-client.jar> <mods folder or jars...>

The client jar is in .minecraft/versions/1.21.1/1.21.1.jar. Exit code 1 when something is missing.

Names come from the jars' language files and models, and a few mods name things there that they never register
(Steam 'n' Rails' tracks for other mods' woods, Better Archeology's "identified artifact"). So also start a server
with the quests once: FTB Quests logs "Tried to load invalid item" for every item that doesn't exist.
"""
import io
import json
import os
import re
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_quests  # noqa: E402

KINDS = ["item", "block", "entity", "biome", "structure", "advancement", "recipe_out", "enchantment",
         "item_tag", "structure_tag", "biome_tag"]


def scan(z, reg):
    for n in z.namelist():
        if n.startswith("META-INF/jarjar/") and n.endswith(".jar"):
            scan(zipfile.ZipFile(io.BytesIO(z.read(n))), reg)
            continue
        m = re.match(r"assets/([^/]+)/lang/en_us\.json$", n)
        if m:
            try:
                d = json.loads(z.read(n).decode("utf-8", "replace"))
            except ValueError:
                continue
            for k in d:
                p = k.split(".")
                if len(p) == 3 and p[0] in ("item", "block", "entity", "biome", "enchantment"):
                    reg[p[0]].add(f"{p[1]}:{p[2]}")
            continue
        for pattern, kind in ((r"assets/([^/]+)/(?:models/item|items)/([a-z0-9_/]+)\.json$", "item"),
                              (r"data/([^/]+)/worldgen/structure/(.+)\.json$", "structure"),
                              (r"data/([^/]+)/worldgen/biome/(.+)\.json$", "biome"),
                              (r"data/([^/]+)/advancements?/(.+)\.json$", "advancement"),
                              (r"data/([^/]+)/enchantment/(.+)\.json$", "enchantment"),
                              (r"data/([^/]+)/tags/items?/(.+)\.json$", "item_tag"),
                              (r"data/([^/]+)/tags/worldgen/structure/(.+)\.json$", "structure_tag"),
                              (r"data/([^/]+)/tags/worldgen/biome/(.+)\.json$", "biome_tag")):
            m = re.match(pattern, n)
            if m:
                reg[kind].add(f"{m[1]}:{m[2]}")
                break
        else:
            m = re.match(r"data/([^/]+)/recipes?/(.+)\.json$", n)
            if m:
                try:
                    outputs(json.loads(z.read(n).decode("utf-8", "replace")), reg["recipe_out"])
                except ValueError:
                    pass


def outputs(o, out):
    if isinstance(o, dict):
        for key in ("result", "results", "output", "outputs"):
            if key in o:
                for x in (o[key] if isinstance(o[key], list) else [o[key]]):
                    if isinstance(x, str):
                        out.add(x)
                    elif isinstance(x, dict):
                        for kk in ("id", "item"):
                            if isinstance(x.get(kk), str):
                                out.add(x[kk])
                            elif isinstance(x.get(kk), dict) and isinstance(x[kk].get("id"), str):
                                out.add(x[kk]["id"])
        for v in o.values():
            outputs(v, out)
    elif isinstance(o, list):
        for v in o:
            outputs(v, out)


# Items that come from the world, mobs or loot rather than a recipe.
FOUND = re.compile(
    r"^minecraft:(.*_ore|raw_.*|.*_sapling|sand|red_sand|.*wool|wheat|bamboo|cocoa_beans|flint|.*_nugget|"
    r"quartz|blaze_rod|breeze_rod|ancient_debris|diamond|lapis_lazuli|echo_shard|trial_key|prismarine_shard|"
    r"totem_of_undying|shulker_shell|elytra|dragon_egg|dragon_breath|chorus_fruit|wither_skeleton_skull|"
    r"netherite_upgrade_smithing_template|ender_pearl|lava_bucket|water_bucket|.*_ingot|redstone|coal|"
    r"rotten_flesh|bone|feather|egg|.*_log|cobblestone|stone|.*_fungus|soul_sand|basalt)$"
    r"|^oritech:raw_.*|^create:(zinc_ingot|crushed_raw_.*)|^born_in_chaos_v1:.*|^betterarcheology:(artifact_shards|unidentified_artifact)"
    r"|^cold_sweat:(goat_fur|hoglin_hide|chameleon_molt)|^farmersdelight:(cabbage|onion|rice|tomato|straw)"
    r"|^create_new_age:(thorium|magnetite_block)|^createdieselgenerators:(crude_oil_bucket|diesel_bucket|gasoline_bucket)"
    r"|^aeronautics:levitite_blend_bucket$")


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    reg = {k: set() for k in KINDS}
    for path in sys.argv[1:]:
        jars = [os.path.join(path, f) for f in sorted(os.listdir(path)) if f.endswith(".jar")] if os.path.isdir(path) else [path]
        for jar in jars:
            try:
                scan(zipfile.ZipFile(jar), reg)
            except zipfile.BadZipFile:
                print("not a jar:", jar)
    items = reg["item"] | reg["block"]
    problems, warnings = [], []

    def need(ok, what, where):
        if not ok:
            problems.append(f"{where}: {what}")

    def tag_or(value, plain, tags):
        return value[1:] in tags if value.startswith("#") else value in plain

    for c in gen_quests.CHAPTERS:
        need(c["icon"] in items, f"chapter icon {c['icon']}", c["key"])
        for q in c["quests"]:
            if q.icon:
                need(q.icon in items, f"icon {q.icon}", q.key)
            for t in q.tasks:
                kind = t["type"]
                if kind == "item":
                    i = t["item"]["id"]
                    need(i in items, f"item {i}", q.key)
                    if i in items and i not in reg["recipe_out"] and not FOUND.match(i):
                        warnings.append(f"{q.key}: {i} has no recipe (check it drops or generates)")
                elif kind == "kill":
                    need(t["entity"] in reg["entity"], f"mob {t['entity']}", q.key)
                elif kind == "biome":
                    need(tag_or(t["biome"], reg["biome"], reg["biome_tag"]), f"biome {t['biome']}", q.key)
                elif kind == "structure":
                    need(tag_or(t["structure"], reg["structure"], reg["structure_tag"]), f"structure {t['structure']}", q.key)
                elif kind == "advancement":
                    need(t["advancement"] in reg["advancement"], f"advancement {t['advancement']}", q.key)
            for r in q.rewards:
                if r["type"] == "item":
                    i = r["item"]["id"]
                    need(i in items, f"reward {i}", q.key)
                    for e in r["item"].get("components", {}).get("minecraft:stored_enchantments", {}).get("levels", {}):
                        need(e in reg["enchantment"], f"enchantment {e}", q.key)
    for w in warnings:
        print("warning:", w)
    for p in problems:
        print("MISSING:", p)
    total = sum(len(c["quests"]) for c in gen_quests.CHAPTERS)
    print(f"{len(gen_quests.CHAPTERS)} chapters, {total} quests: {len(problems)} missing, {len(warnings)} warnings")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
