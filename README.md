# The Pale

A NeoForge 1.21.1 mod (Java 21). Land ends about 1250 blocks from (0, 0). Past it lies a grey, poisonous ocean, the
Pale, behind a drifting wall of fog.

- Common config: `config/thepale-common.toml` (world shape, Pale biome radius, poison).
- Client config: `config/thepale-client.toml` (fog wall and camera fog).
- The finale (the invasion, the boss, the cleansing of the Pale): see [The finale](#the-finale).

## Three islands

New worlds have three islands with the Pale between them:

| Island | Where | Climate | Notes |
|---|---|---|---|
| Mild (starter) | centre (0, 0) | temperate: forests, dark forest, plains, taiga | spawn |
| Hot | 3500 to 4500 blocks south, up to 500 east or west | warm and wet to the west (jungle), hot and dry to the east (desert, badlands) | strongholds and the End portal |
| Cold | 3500 to 4500 blocks north, up to 500 east or west | frozen: snowy taiga, snowy plains, ice spikes | |

Each island is 1000 to 1500 blocks in radius with its own ragged coast (lobes, bays and headlands), all different on
every seed. At least 1000 blocks of open sea always lie between two coasts, so the poison between islands is never
thin. The core of every island is land even where the seed has open sea.

The radii in the common config are measured from each island's coast: `landEndRadius` is the coast itself,
`paleBiomeRadius` 50 blocks past it, the poison 250 and 750 past it, and the client's fog wall 150 to 750 past it.
`coastRoughness` in `[world]` sets how ragged the coasts are (0 = round), `threeIslands = false` makes one island.
Both are fixed when a world is created and saved with it (`thepale-world.json`). Worlds made with 1.6.0 keep their one
ragged island, older worlds stay round. The server sends the layout to each client that joins.

**Getting around**

- **Hot island:** through the Nether (4000 blocks are 500 there). The Nether itself is untouched.
- **Cold island:** only on elytra. From 450 blocks up the Pale thins out and from 500 there's no fog and no poison;
  below lies a sea of clouds (y 335 to 435) with the islands as holes in it. A portal from the Nether won't open a
  new portal on the cold island (or in the Pale around it): the player sees "Something on the other side holds the
  portal shut". A portal built there by somebody who flew in works both ways. The clean air up high is for wings
  only: a player standing on something or riding it there (a Create Aeronautics airship, a seat) gets the full poison.
- **The End:** the three strongholds are on the hot island, half way out from its centre. Eyes of ender lead there
  from anywhere.

From high up the ground is hidden by the game's own distance fog beyond your render distance (12 chunks: about 190
blocks, up and down too), so an island shows up as a hole in the clouds but its land only once you come down to about
y 300. With Distant Horizons the sea of clouds and the islands reach the horizon.

## Structures on the island

On an island this small, many structures would never show up. Three things help, all in `[structures]` of the common
config and all only for new chunks:

- **Rare structures spread out** (`maxStructureSpacing`, 40 chunks): structure sets spaced wider than that (woodland
  mansions 80, Born in Chaos 50 to 200, some Terralith ones) are spaced 40 chunks apart in the Overworld, so they get
  about a dozen tries on the island. Their biome rules stay.
- **Seed search** (`searchSeed`, `requiredStructures`, `maxSearchSeconds`): when a world is created with a random
  seed (empty seed field, or empty `level-seed` on a server), the mod tries seeds for up to a minute and keeps the one
  with the most of `requiredStructures` on the island. A seed you type in is kept.
- **Pinning** (`pinMissingStructures`): whatever is still missing gets one try of its structure set moved to a spot on
  the island where it fits (right biome, workable ground). Works for typed seeds too. The pins are saved in the world
  folder (`thepale-structure-pins.json`) and loaded on every start. `/locate` and explorer maps find pinned
  structures like any other.

A structure whose biome isn't on the island at all can't be placed; the first player to join is told what's missing.
`requiredStructures` lines can name alternatives (`a|b`) and prefixes (`minecraft:village_*`); lines for mods that
aren't installed are skipped.

**Outposts keep away from villages** (`keepAwayFromVillages`, `villageStructureSets`, `villageClearance`): vanilla
keeps pillager outposts 10 chunks from its own villages only, so Terralith's fortified villages (and villages moved by
a pin) could get a pillager tower in the middle, and Terralith's desert outposts ignored villages entirely. Now
`minecraft:pillager_outpost` and `terralith:desert_outpost` don't start within 10 chunks of a vanilla village or a
Terralith fortified village. `/locate`, the seed search and the pins see the same. Only affects newly generated chunks.

`/thepale survey` (operators) reports the island: the biomes with their share, every structure with how many and
where one is, and which biomes and structures the world has that the island doesn't. It writes
`thepale-survey.txt` and a biome map, `thepale-survey.png`, into the world folder. It reads the world generator, so
nothing gets generated.

## Other mods

- **North-only mobs** (`frozenIslandOnlyMobs` in `[mobs]`): Born in Chaos' Lord Pumpkinhead, Krampus, Missioner,
  Nightmare Stalker, Dire Hound Leader, Lifestealer and Mother Spider appear only on the frozen island: not on the
  starter or hot island, not in the Pale, not in the Nether or the End. Natural spawns, spawners and mod events are
  all stopped; spawn eggs and `/summon` still work. The Transmuting Elixir is refused on an Infernal Evil Pumpkin
  outside the north (the elixir and the pumpkin are kept, and the player is told only the north answers). The server
  log lists the matches at start (`Only on the frozen island`).
- **Rampage speed** (`rampageSpeedBonus` in `[effects]`): the movement speed bonus of Born in Chaos' five Rampage
  levels is 5, 10, 15, 20 and 25% instead of the mod's 15, 30, 50, 75 and 100%. Damage and attack speed are left
  as they are. Covers every source of Rampage (Great Reaper Axe, full Dark Metal set at low health, the potion);
  the server log says `Rampage movement speed set to` at start.
- **Dark Metal set** (`darkMetalSetStrength` in `[effects]`): wearing all four pieces of Born in Chaos' Dark Metal
  armour gives Strength I (0 turns it off). Strength also keeps the Dark Warblade from tiring its wielder.
- **Mob spawns:** the Pale has none. Mods that add their mobs to every biome (`neoforge:any`, as Born in Chaos does)
  are undone by `data/thepale/neoforge/biome_modifier/the_pale_no_spawns.json`; the server log says
  `The Pale biome has no mob spawns` at start.
- **Structures:** the Pale biome is in no biome tag, so structures restricted by biome or tag (all of Born in Chaos'
  and Terralith's) never generate in it.
- **Terralith:** builds its terrain on `minecraft:overworld/continents`, which The Pale reshapes, and its biome list is
  handled too, since the Pale is switched on for whatever biome source the Overworld uses.
- **Oritech:** its resource nodes (the boulders of deepslate and ore it scatters on the surface for its deep drill)
  are taken out of world generation everywhere (`data/thepale/neoforge/biome_modifier/no_oritech_resource_nodes.json`).
  Its ores, oil springs and uranium patches stay. Without resource nodes the deep drill has nothing to mine.
- **Ores and oil by island** (`[ores]`): `frozenIslandOnlyFeatures` keeps Oritech's nickel, platinum and uranium and
  Create New Age's thorium on the frozen island, `hotIslandOnlyFeatures` keeps Oritech's oil springs on the hot one.
  Create Diesel Generators' oil isn't a feature: The Pale replaces its `createdieselgenerators:oil_biomes` tag with
  desert, badlands and savanna biomes, which only the hot island has.
- **Create Aeronautics (Sable):** `data/thepale/dimension_physics/overworld.json` sets the Overworld's air pressure:
  Sable's own curve up to y 240, then down to nothing at y 300. Balloons and propellers lose their lift and thrust
  with the pressure, so no airship climbs to the clean air from y 450. (Levitite only holds a ship up, it doesn't
  lift it.)
- **Better Combat:** Born in Chaos' Dark Warblade fights as a claymore, the Great Reaper Axe as a double axe, the
  Skullbreaker Hammer as a hammer, the pitchfork as a spear and the Nightmare Scythe as a scythe
  (`data/born_in_chaos_v1/weapon_attributes/`). The pack's fallback config leaves Born in Chaos' staffs out.
- **Sodium:** works; the fog wall, camera fog and ash render as usual.
- **Shader packs (Iris, e.g. Complementary):** packs draw their own fog and ignore the game's, and anything drawn
  into the world for them comes out as hard sheets. So with a pack on, the Pale is drawn as steam, like the Basalt
  Deltas' haze: once the level is drawn, every pixel's view ray is marched through the fog up to whatever the pixel
  shows (`shaders/steam/steam.fsh`), and the result is laid over the pack's finished frame before the HUD. The density
  at each point is what the camera fog would be there, so the steam thickens out into the Pale, its front billows and
  drifts (slowly rising), the air clears from y 450 to 500 with a lumpy top that is the sea of clouds from above, and
  the water in front of the bank reflects it. Near things (the hand, the ash, the island in front of the bank) stay
  clear; at night the steam is moonlit grey rather than black. The layered wall, the sea-of-clouds sheets and the
  Distant Horizons cloud slabs aren't drawn then. It is plain OpenGL that puts back every bit of state it touches;
  if it can't run on a machine, the player is told once in chat, the log says why, and the older look (fog over the
  frame, the wall as particles) is used.
  Iris is optional and only looked up by reflection; the client log says `Shader pack on` when the switch happens.
- **Water colour:** the water turns from blue to the Pale grey over the 250 blocks before the fog wall, in every
  biome, through a small client mixin on the water colour resolver (works with Sodium).

## The way in

Nothing changes at a line: over the last 120 blocks before the inner radius (1280 to 1400 by default) the fog closes
in from your render distance to `fogEntryVisibility`, and the fog and sky turn grey a little behind it (fully grey by
about 1490). The Pale biome itself has ordinary ocean sky and fog colours, so where it starts (1300) can't be seen.

## Distant Horizons

Optional. With Distant Horizons 3.x installed (tested with 3.3.3):

- The fog wall also shows in DH's far terrain, drawn the way DH draws its clouds: stacked, softly shaded cloud slabs
  scattered over the band, thicker toward the outer radius. Near the camera it hands over to the normal fog wall.
  With a shader pack the steam reaches the horizon itself instead, and reads DH's depth through Iris so far LOD land
  in front of the bank isn't fogged.
- Inside the Pale, DH's fog is pulled in to match the Pale fog, and once that fog is closer than the vanilla chunks
  DH stops drawing, so its far terrain never shows through.
- DH's generic object rendering is off on macOS, so there the far cloud wall doesn't show.

The DH jar in `libs/` is only compiled against; it is not bundled.

## The finale

The end of the pack, from 1.13.0: the players call the thing that lives in the fog, kill it, and cleanse the Pale.
Everything is in `[finale]` of `config/thepale-common.toml`; the state of a world is saved in
`data/thepale_finale.dat` of the Overworld. Requires GeckoLib 4.9 or newer.

### 1. The invasion

`/thepale invasion start` (permission level 2; the quest "Summon the Pale" runs it as a command reward) starts it.
There is no way to lose it and, short of `cancel`, no way back.

| When | What happens |
|---|---|
| Day 1 | A message and a sound for everybody; the sky and the fog darken toward purple, more every day. |
| From day 2 (`silhouetteFromDay`) | A silhouette stands on the horizon, drawn by the client from a position the server sends: no entity exists yet. |
| End of day 3 (`approachDays`) | It waits off the shore of the island with the most players, at the point of the coast nearest to them, until it is night and a player is within `spawnRange`. Then the boss comes ashore. |

Time is counted in game days, so sleeping and `/time add` move it along. While nobody is on any island it waits. The
boss stays within `bossLeash` blocks of its landing point and breaks no blocks (`blockBreakRadius` is reserved, 0 =
nothing). If the arena (`arenaRadius`) stays empty for `retreatMinutes`, it goes back into the sea, heals, and returns
the next night.

| Command | |
|---|---|
| `/thepale invasion start` | Starts the invasion. |
| `/thepale invasion status` | Phase, progress, where the boss is, whether the Pale is cleansed. |
| `/thepale invasion skip` | Jumps to the next stage (for testing): approach, waiting, ashore. |
| `/thepale invasion cancel` | Calls it off and removes the boss. |
| `/thepale boss spawn` | The boss right here, outside any invasion: for trying the fight. It still drops the heart. |
| `/thepale cleanse` | Cleanses the Pale without the heart. `status` shows the radii, `undo` brings the fog and poison back for testing (biomes already turned and terrain already generated stay). |

### 2. The Patchwork Abomination

`thepale:patchwork_abomination`: a stitched ball hovering over the ground on six tentacles, with a stone face and a
clawed hand on two more. Every limb, the body and the main eye are their own hitboxes (`PartEntity`); limbs have their
own health and are cut off for good. Only damage dealt by players counts. The boss bar shows within the arena.

| Phase | The boss | To get on |
|---|---|---|
| 1. Tentacles | Body takes `armoredDamageFactor` (x0.2) and can't drop below the next phase's threshold. Tentacles slam down: a ring of particles marks the spot `slamWindupTicks` before (`slamDamage`, `slamRadius`). Each tentacle cut lowers the body. | Cut tentacles until `phase2TentaclesLeft` are left. |
| 2. Face and hand | The face comes down and casts in turn: a scream (slowness and blindness), a summon (`summonMobs`, zombies if none of them exists), a poison cloud. The hand grabs a player, drags and throws them; hitting the hand lets go. | Destroy the face, the hand and the last tentacles. |
| 3. Seams | The seam parts and the main eye opens for `eyeOpenSeconds`, shut for `eyeClosedSeconds`. Open eye: x`eyeDamageFactor`; shut: x`closedEyeDamageFactor`. Shockwaves along the ground while it is shut. | Bring the body down to `fallHealthFraction`. |
| 4. Fall | It falls and crawls after players; touching it and its shockwaves hurt (`crawlDamage`). Full damage anywhere. | Kill it. |

Health (`bodyHealth` 600, `tentacleHealth` 50, `faceHealth` and `handHealth` 100) is meant for a full netherite or
Born in Chaos set with Better Combat; each player in the arena beyond the first adds `extraPlayerHealth` (50%).
It drops the Heart of the Pale (never despawns, can't be destroyed), loot
(`data/thepale/loot_table/entities/patchwork_abomination.json`) and `experience`, and everybody near gets the
advancement `thepale:finale/abomination_slain`.

Sounds are the mod's own events (`assets/thepale/sounds.json`, `thepale:finale.*` and `thepale:boss.*`), for now
pointing at vanilla sounds with a changed pitch: a resource pack or new .ogg files replace them without code changes.

### 3. The cleansing

Using the Heart of the Pale (hold for three seconds, in the Overworld) cleanses the Pale. It is the heart and not the
boss's death that does it, because it changes the world for good: everybody can be there for it, and a boss summoned
to try the fight changes nothing by dying.

- The poison stops. Clients fade the fog wall, the fog, the ash, the grey water, the sea of clouds and the Distant
  Horizons fog over `fadeSeconds`.
- Chunks that exist keep their terrain. Their `thepale:the_pale` biome becomes the ocean biome the seed has there, as
  each chunk loads and `chunksPerTick` loaded chunks a tick, and is sent to the players again.
- New chunks: up to `preserveMargin` blocks past the farthest chunk that existed (read from the region files), open
  sea exactly as before, so old and new chunks never meet at a seam. Over the next `transitionWidth` blocks the pull
  toward the ocean eases off, and beyond that the world is the seed's own: continents across the sea. The islands'
  climates fade out over the same band.
- No restart is needed: world generation reads the state live. One restart afterwards is still a good idea, because
  `/locate`, explorer maps and eyes of ender may have cached answers for far-away chunks from before.

What stays and what goes after the cleansing:

| Rule | After the cleansing |
|---|---|
| Poison, fog wall, Pale biome | Gone everywhere. |
| No new Nether portals on the frozen island | Lifted (`openColdIslandPortals`, on by default). |
| `notOnStarterIsland` mobs | Kept on the starter island; not applied in the new lands. |
| `frozenIslandOnlyMobs`, island-only ores and structures | Kept on the three islands; in the new lands everything generates and spawns as the seed and the mods have it. |
| Strongholds | Stay on the hot island. |
| Pinned structures | Unchanged: pins are only ever placed on the islands. |
| `maxStructureSpacing` | Still applies to the whole Overworld, so rare structures are a little denser in the new lands than in vanilla. |

Known limits: Distant Horizons keeps the LODs it already drew of the old sea until a player goes there; with a shader
pack the steam fades by density (not tried with a shader pack in testing); if somebody once generated chunks very far
out, the new lands start past those unless `maxSeaDistance` is set.

### Replacing the boss model

The model in the jar is a draft, generated by `tools/gen_boss_model.py` (geometry, animations and textures). An
artist's model replaces these files, with no code changes as long as the bone and animation names are kept:

- `assets/thepale/geo/entity/patchwork_abomination.geo.json`
- `assets/thepale/animations/entity/patchwork_abomination.animation.json`
- `assets/thepale/textures/entity/patchwork_abomination.png` and `patchwork_abomination_glowmask.png` (the parts
  that glow in the dark: the slits and the eyes)

Bones. The origin is the lowest point of the body, the front (the main eye) is toward -Z (north in Blockbench),
1 block = 16 units:

| Bone | What the code does with it |
|---|---|
| `body` | Nothing: a ball about 4.5 blocks in radius, animated by `idle`, `crawl`, `death`. Parent of the next five. |
| `slits` | The small glowing eye slits. |
| `eye_main`, `eyelid_upper`, `eyelid_lower` | The main eye, animated by `eye_open` / `eye_shut`. |
| `seam_left`, `seam_right` | The seam over the eye, animated by `seam_open` / `seam_shut`. |
| `tentacle1_seg1` ... `tentacle6_seg8` | One bone per segment, **not parented to each other**. Each is modelled hanging straight down from its pivot, 2 blocks (32 units) long. Every frame the code puts each pivot on the limb's curve and turns the bone along it, and hides the limb when it is cut off. |
| `face_seg1..8`, `hand_seg1..8` | The same for the two limbs that carry the face and the hand. |
| `face`, `face_jaw` | The stone face, its pivot where the last segment ends, facing -Z. `face_jaw` is its child, moved by `face_scream`. |
| `hand`, `hand_finger1..5` | The hand, modelled hanging down from its pivot like a segment; the fingers are its children, moved by `hand_open` / `hand_grab`. |

Bones the model doesn't have are skipped, and extra bones are fine as children of these. Where the limbs are attached
and how long they are (8 segments of 2 blocks) is in `finale/boss/Limbs.java`, shared by the hitboxes and the model:
change both together.

Animations, by these exact names. Looping: `idle`, `crawl` (phase 4), `face_idle`.
Played once and held on the last frame: `death` (5 s, the entity is removed after it), `seam_shut`, `seam_open`,
`eye_shut`, `eye_open`, `face_scream` (once per scream), `hand_open`, `hand_grab`. They must not key the segment
bones, `face` or `hand` themselves: the code owns those.

## Credits

The world shape is based on [Oceanus](https://modrinth.com/datapack/oceanus) by **Lizdtre**, used under the MIT
License. The Pale reimplements its continentalness falloff as its own density function type
(`thepale:radial_falloff`) instead of depending on More Density Functions.

> MIT License
>
> Copyright (c) Lizdtre
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
> documentation files (the "Software"), to deal in the Software without restriction, including without limitation
> the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
> permit persons to whom the Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or substantial portions of
> the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
> THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
> TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.
