# The Pale

A NeoForge 1.21.1 mod (Java 21). Land ends about 1250 blocks from (0, 0). Past it lies a grey, poisonous ocean, the
Pale, behind a drifting wall of fog.

- Common config: `config/thepale-common.toml` (world shape, Pale biome radius, poison).
- Client config: `config/thepale-client.toml` (fog wall and camera fog).

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
  portal shut". A portal built there by somebody who flew in works both ways.
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
- **Sodium:** works; the fog wall, camera fog and ash render as usual.
- **Shader packs (Iris, e.g. Complementary):** packs draw their own fog and ignore the game's, so with one on The
  Pale lays its fog over the finished frame instead (before the HUD). In the open sea of the Pale the distance each
  pixel sees is where its view ray meets the water, so near water stays clear and the sky, clouds and the island's
  skyline disappear into the grey. The fog wall is drawn through the game's particle shader (packs can't run its own
  shader), with a generated cloud texture, and pulled in to within the pack's fog so it shows from the island. Iris is
  optional and only looked up by reflection; the client log says `Shader pack on` when the switch happens.
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
- Inside the Pale, DH's fog is pulled in to match the Pale fog, and once that fog is closer than the vanilla chunks
  DH stops drawing, so its far terrain never shows through.
- DH's generic object rendering is off on macOS, so there the far cloud wall doesn't show.

The DH jar in `libs/` is only compiled against; it is not bundled.

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
