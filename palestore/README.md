# Pale Store (NeoForge 1.21.1, JEI 19.70)

A recipe store inside JEI. Some crafting recipes are locked until a quest opens them:

| Offer | Locks | Opened by quest (chapter 1) |
|---|---|---|
| `palestore:bed` | every bed (`#minecraft:beds`) | Stone Age |
| `palestore:ladder` | ladder | Torches |
| `palestore:diamond_pickaxe` | diamond pickaxe | Into the Nether |

Each offer is unlocked by an advancement `palestore:unlock/<name>` that has no trigger of its own; the FTB Quests
reward runs `/advancement grant @p only palestore:unlock/<name>`. `palestore:quest/has_bed` and
`palestore:quest/has_cooked_meat` are helper advancements the quests check (any bed, any cooked meat).

## Build
Needs Java 21 and internet (Gradle downloads NeoForge/Minecraft).

    ./gradlew build          (Windows: gradlew.bat build)

The mod jar appears in `build/libs/palestore-<version>.jar`. Put it in your instance's `mods` folder
next to JEI and your other mods. Test in a dev client with `./gradlew runClient`.

## How it works
- `ShopOffers`            what is locked and which advancement unlocks it
- `ServerLogic`           advancement sync
- `AdvancementResultSlot` hides locked outputs and blocks taking them at the result slot
- `Unlocks`               checks a player's saved advancement progress
- `net/*`                 unlock list sync (server -> client)
- `jei/StoreCategory`     the "Recipe Store" tab: item and advancement requirement
- `jei/PaleStoreJeiPlugin` registers the tab and hides/shows crafting recipes in JEI
- `mixin/AnvilMenuMixin`  anvil is free: no level requirement, no level cost, no "Too Expensive!"
- `enchant/*`             Pale Enchantments (merged in 0.6.0): Leech, Vorpal, Soulbound, Smashing, Smelting; Insight
                          is pure data. The enchantments themselves are in `data/paleenchants/enchantment`
- `mixin/enchant/*`       Smashing is 10x rarer at the table and in random loot
- `client/XpHud`          hides the XP bar and level, shows total XP points instead (level 7 -> 100)

## Enchantments

| Enchantment | Max | Items | Effect |
|---|---|---|---|
| Insight | III | tools and weapons | +50% / +100% / +150% XP from blocks and mobs |
| Leech | IV | weapons | heals 1 heart per level on each kill |
| Vorpal | III | weapons | 10/15/20% chance of 1.5x/1.75x/2x damage, 5/10/15% chance to drop the victim's head |
| Soulbound | III | anything | kept on death; each death has a 1 in (level + 1) chance to lose a level |
| Smashing | I | pickaxes | ores drop Create crushed ore (or twice the drop); very rare |
| Smelting | I | mining tools | drops come out smelted, with furnace XP |

The table offers at most level III. Higher levels (Leech IV) only come from the anvil.

## Testing
- In JEI press R on a Diamond Pickaxe: while locked you only see the "Recipe Store" tab.
  Press U on an Emerald to open the store directly.
- `/advancement grant @s only palestore:unlock/diamond_pickaxe` unlocks it by hand; `revoke` locks it again.

## Known limits
- Crafting result slots are gated directly, so locked results should not flash or be obtainable by shift-clicking.
- Only crafting is locked. The item can still come from loot, villagers, other mods' machines or the Crafter block.
