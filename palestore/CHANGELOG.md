# Pale Store Changelog

Changes made during this conversation, from the supplied 0.3.0 source through the current 0.5.2 build.

## 0.6.0 — Current

- Merged Pale Enchantments 1.0.0 into Pale Store; the separate `paleenchants` jar is no longer needed (remove it from `mods`).
- Six enchantments: Insight, Leech, Smashing, Smelting, Soulbound and Vorpal. They come from the enchanting table and from dungeon/chest loot.
- The enchanting table offers at most level III (as in 0.5.2); Leech IV only comes from combining two Leech III on the anvil.
- The enchantments keep their `paleenchants:` ids, so items enchanted with Pale Enchantments 1.0.0 keep them.
- Smashing stays ten times rarer than its weight: nine times in ten it is dropped from table offers and random loot.

## 0.5.2

- Fixed clicks on level II and III enchanting offers. The client now lets the selection reach the server; the server checks the bookshelf requirement.
- Set bookshelf thresholds to 8 valid bookshelves for level II and 15 for level III.
- Kept vanilla's bookshelf power calculation and enchantment offer selection. The three choices target enchantment levels I, II, and III, capped at each enchantment's maximum level.
- Made enchanting consume exact total XP points: 125, 250, or 500 for choices I, II, and III.
- Replaced the vanilla XP level display with the player's total XP points and hid the vanilla XP bar.

## Earlier changes in this conversation

- Replaced the Diamond Pickaxe's 100 XP test unlock with a crafting unlock tied to the **We Need to Go Deeper** advancement (entering the Nether). The locked crafting result is hidden until the advancement is earned, and unlock state syncs on login, respawn, and advancement completion.
- Fixed the brief appearance of the locked pickaxe crafting result before the lock took effect.
- Fixed mixin/startup issues encountered while updating the crafting unlock and enchanting behavior.

## Build target

- Minecraft Java 1.21.1
- NeoForge 21.1.265
