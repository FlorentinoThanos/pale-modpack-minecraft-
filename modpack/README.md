# ZOMBIEageISLAND (модпак)

Версия 1.71. Minecraft 1.21.1, NeoForge 21.1.265.

- `modrinth.index.json` — список модов с Modrinth (лаунчер скачивает их сам).
- `overrides/config/` — конфиги сборки.

Моды, которых нет в `modrinth.index.json` (в сборке они лежат в `overrides/mods/`, в репозиторий не добавлены):

- The Pale — собирается из этого репозитория (`./gradlew build`, jar в `build/libs/`), или скачать со страницы Releases.
- Pale Store 0.6.0 — собирается из папки `palestore/` (`cd palestore && ./gradlew build`).
- FTB Library 2101.1.39, FTB Teams 2101.1.11, FTB Chunks 2101.1.23, FTB Quests 2101.1.37 (CurseForge).

## Квесты (FTB Quests)

Квестовая линия 2.0 лежит в `overrides/config/ftbquests/quests/`: 12 глав и 319 квестов на русском и английском
(`lang/`). Главы по островам: Тихий остров → Путь на юг → Горячий остров → Край → Холодный остров. Рядом с ними
боковые главы для модов: Кухня (Farmer's Delight), Археология (Better Archeology), Механика (Create), Нефть и ток
(Diesel Generators, Create New Age, Oritech), Небо (Create Aeronautics), Бестиарий хаоса (Born in Chaos) и Зачарования.

Файлы собраны скриптом `tools/gen_quests.py` (`python3 modpack/tools/gen_quests.py`). Id квестов считаются из их ключей,
поэтому прогресс игроков переживает пересборку, пока ключ не переименован. `tools/check_quests.py` сверяет все
предметы, мобов, биомы, структуры и достижения с jar-файлами сборки:
`python3 modpack/tools/check_quests.py <1.21.1.jar> <папка mods>`.

Если правишь квесты в игре, дальше правь там и скрипт больше не запускай: он перезапишет главы.

## Что поменялось в 1.71

- The Pale 1.10.0: потолок для дирижаблей, торий только на севере, нефть только на юге, Better Combat для оружия
  Born in Chaos (подробности в главном README).
- Cold Sweat: супы и рагу Farmer's Delight греют, горячий шоколад греет сильнее, эскимо и дынный сок охлаждают
  (`coldsweat/item.toml`, «Temperature-Affecting Foods»).
- Better Combat: посохи Born in Chaos исключены из боя ближнего типа (`bettercombat/fallback_compatibility.json`).
- Квесты 2.0.
