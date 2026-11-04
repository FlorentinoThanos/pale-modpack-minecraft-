# ZOMBIEageISLAND (модпак)

Версия 1.69. Minecraft 1.21.1, NeoForge 21.1.265.

- `modrinth.index.json` — список модов с Modrinth (лаунчер скачивает их сам).
- `overrides/config/` — конфиги сборки.

Моды, которых нет в `modrinth.index.json` (в сборке они лежат в `overrides/mods/`, в репозиторий не добавлены):

- The Pale — собирается из этого репозитория (`./gradlew build`, jar в `build/libs/`), или скачать со страницы Releases.
- Pale Store 0.6.0 — из PR #1 (ветка `palestore-enchantments`).
- FTB Library 2101.1.39, FTB Teams 2101.1.11, FTB Chunks 2101.1.23, FTB Quests 2101.1.37 (CurseForge).
