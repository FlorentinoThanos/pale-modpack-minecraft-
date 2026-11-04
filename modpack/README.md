# ZOMBIEageISLAND (модпак)

Minecraft 1.21.1, NeoForge 21.1.265.

- `modrinth.index.json` — список модов с Modrinth (лаунчер скачивает их сам).
- `overrides/config/` — конфиги сборки.

Моды, которых нет в `modrinth.index.json` (в сборке они лежат в `overrides/mods/`, в репозиторий не добавлены):

- The Pale — собирается из этого репозитория (`./gradlew build`, jar в `build/libs/`), или скачать со страницы Releases.
- FTB Library 2101.1.39, FTB Teams 2101.1.11, FTB Chunks 2101.1.22, FTB Quests 2101.1.37 (CurseForge).

## Квесты (FTB Quests)

Квестовая линия лежит в `overrides/config/ftbquests/quests/`: 6 глав по островам (Тихий остров → Путь на юг →
Горячий остров → Край → Холодный остров) и боковая глава «Зачарования». Тексты на русском и английском (`lang/`).
Файлы собраны скриптом `tools/gen_quests.py` (`python3 modpack/tools/gen_quests.py`). Если правишь квесты в игре,
дальше правь там и скрипт больше не запускай: он перезапишет главы.
