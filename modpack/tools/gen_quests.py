#!/usr/bin/env python3
"""Generates the FTB Quests files of the modpack (modpack/overrides/config/ftbquests/quests).

Run from anywhere:  python3 modpack/tools/gen_quests.py
It rewrites chapters/, chapter_groups.snbt and lang/{en_us,ru_ru}.snbt. data.snbt is left alone.
Quest ids are derived from the quest keys below, so they stay the same between runs and player
progress survives a regeneration as long as a key is not renamed.
Once you start editing quests in the in-game editor, edit there instead and stop using this script.

Version 2.0: twelve chapters, with the mods of the 1.69 pack (Farmer's Delight, Better Archeology, Create Aeronautics,
Diesel Generators, Create New Age, Steam 'n' Rails, Better Combat, Corpse, Carry On, Elytra Slot).
Check ids against the pack with modpack/tools/check_quests.py.
"""
import hashlib
import os
import shutil

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "overrides", "config", "ftbquests", "quests")


class Int(int):
    pass


class Long(int):
    pass


class IntArray(list):
    """Written as an SNBT int array: [I; 1, 2, 3]."""


def qid(key):
    # FTB Quests reads ids as signed longs and silently replaces any id with the top bit set (first hex digit 8-F)
    # with a random one, which breaks dependencies. So the top bit is cleared; ids that already had it clear stay.
    v = int(hashlib.sha1(("thepale:" + key).encode()).hexdigest()[:16], 16) & 0x7FFFFFFFFFFFFFFF
    return f"{v:016X}"


# ---------------------------------------------------------------- tasks
def item(i, n=1):
    return {"type": "item", "item": {"id": i, "count": 1}, "count": n}


def kill(e, n=1):
    return {"type": "kill", "entity": e, "value": n}


def dim(d):
    return {"type": "dimension", "dimension": d}


def biome(b):
    return {"type": "biome", "biome": b}


def structure(s):
    return {"type": "structure", "structure": s}


def adv(a):
    return {"type": "advancement", "advancement": a, "criterion": ""}


def stat(st, n):
    return {"type": "stat", "stat": st, "value": n}


def check():
    return {"type": "checkmark"}


def area(dimension, x, y, z, w, h, d):
    # FTB Quests reads these as int arrays "position" [x, y, z] and "size" [w, h, d]
    return {"type": "location", "dimension": dimension, "ignore_dimension": False,
            "position": IntArray([x, y, z]), "size": IntArray([w, h, d])}


# ---------------------------------------------------------------- rewards
def xp(n):
    return {"type": "xp", "xp": n}


def give(i, n=1):
    return {"type": "item", "item": {"id": i, "count": n}}


def potion(p):
    return {"type": "item", "item": {"id": "minecraft:potion", "count": 1, "components": {
        "minecraft:potion_contents": {"potion": p}}}}


def unlock(name):
    """Pale Store: opens a locked recipe (palestore:unlock/<name>, see ShopOffers.java)."""
    return {"type": "command", "command": f"/advancement grant @p only palestore:unlock/{name}",
            "elevate_perms": True, "permission_level": 2, "silent": True}


def table_long(key):
    return int(qid("table/" + key), 16)


def choice(key):
    return {"type": "choice", "table_id": Long(table_long(key))}


def rand(key):
    return {"type": "random", "table_id": Long(table_long(key))}


def book(enchant, level=1):
    return {"type": "item", "item": {"id": "minecraft:enchanted_book", "count": 1, "components": {
        "minecraft:stored_enchantments": {"levels": {enchant: level}}}}}


# ---------------------------------------------------------------- quests
# Q(key, x, y, ru_title, en_title, ru_desc, en_desc, tasks, rewards, deps, icon=None, shape=None, size=None)
class Q:
    def __init__(self, key, x, y, ru, en, ru_d, en_d, tasks, rewards, deps=(), icon=None, shape=None, size=None,
                 extra=None):
        self.__dict__.update(locals())
        del self.__dict__["self"]


CHAPTERS = []
TABLES = []

# Chapters written into the quest book. 3.0 is being rewritten with Max chapter by chapter; the 2.0 chapters below
# stay in this file as material but are not written until they are redone.
BUILD = ["mild", "nether"]


def table(key, ru, en, rewards):
    TABLES.append(dict(key=key, ru=ru, en=en, rewards=rewards))


def chapter(key, icon, ru, en, ru_sub, en_sub, quests, scale=1.0):
    CHAPTERS.append(dict(key=key, icon=icon, ru=ru, en=en, ru_sub=ru_sub, en_sub=en_sub, quests=quests, scale=scale))


# ================================================================ 1. Тихий остров (3.0, составлена вместе с Max)
# Прямой путь 1..8 по ряду y = 0, ветки по схеме Max (notes/quests/chapter-1-scheme.png в файлах проекта).
FIRE_RES = potion("minecraft:fire_resistance")          # 3:00
LONG_FIRE_RES = potion("minecraft:long_fire_resistance")  # 8:00

table("iron_tool", "Железный инструмент", "Iron Tool",
      [give("minecraft:iron_hoe"), give("minecraft:iron_shovel")])
table("insight_book", "Книга «Озарение»", "Insight Book", [book("paleenchants:insight", n) for n in (1, 2, 3)])
table("efficiency_book", "Книга «Эффективность»", "Efficiency Book", [book("minecraft:efficiency", n) for n in (1, 2, 3)])
table("protection_book", "Книга «Защита»", "Protection Book", [book("minecraft:protection", n) for n in (1, 2, 3)])

chapter("mild", "minecraft:oak_sapling", "Тихий остров", "The Mild Island",
        "Главный путь: от первого верстака до перехода на горячий остров.",
        "The main path: from your first crafting table to the crossing to the hot island.", [
    # ---- прямой путь
    Q("mild/welcome", 0, 0, "Серый горизонт", "Grey Horizon",
      "Земля кончается примерно в 1250 блоках отсюда. Дальше серое ядовитое море и стена тумана. Где-то за ним есть ещё два острова. Начни с малого.",
      "Land ends about 1250 blocks from here. Past it lies a grey, poisonous sea behind a wall of fog. Two more islands wait somewhere beyond it. Start small.",
      [check()], [give("minecraft:bread", 8)], icon="minecraft:compass", shape="hexagon", size=1.5),
    Q("mild/table", 1, 0, "Верстак", "Crafting Table", "Сделай верстак.", "Craft a crafting table.",
      [item("minecraft:crafting_table")], [xp(50)], ["mild/welcome"]),
    Q("mild/stone", 2, 0, "Каменный век", "Stone Age",
      "Сделай каменную кирку. В награду откроется рецепт кровати: до этого его нет.",
      "Make a stone pickaxe. The reward unlocks the bed recipe, which is locked until then.",
      [item("minecraft:stone_pickaxe")], [unlock("bed")], ["mild/table"]),
    Q("mild/bed", 3, 0, "Своя кровать", "A Bed of Your Own", "Сделай кровать любого цвета. Теперь ночь можно проспать.",
      "Make a bed of any colour. Now you can sleep through the night.",
      [adv("palestore:quest/has_bed")], [xp(75)], ["mild/stone"], icon="minecraft:red_bed"),
    Q("mild/iron", 4, 0, "Железная кирка", "Iron Pickaxe", "Железная кирка добывает алмазы. Огнестойкость в награду пригодится у лавы.",
      "An iron pickaxe can mine diamonds. The fire resistance reward helps around lava.",
      [item("minecraft:iron_pickaxe")], [FIRE_RES], ["mild/bed"]),
    Q("mild/diamonds", 5, 0, "Алмазы", "Diamonds",
      "Добудь 5 алмазов. Алмазную кирку пока не скрафтить: её рецепт откроется в Незере.",
      "Mine 5 diamonds. You can't craft a diamond pickaxe yet: its recipe unlocks in the Nether.",
      [item("minecraft:diamond", 5)], [give("minecraft:book", 5), xp(100)], ["mild/iron"]),
    Q("mild/nether", 6, 0, "В Незер", "Into the Nether",
      "Обсидиан без алмазной кирки не добыть, поэтому портал отливают на месте: выложи форму, залей лаву в каждую ячейку рамки и полей водой. Ещё обсидиан бывает в разрушенных порталах. Войди в Незер: откроется рецепт алмазной кирки и глава «Незер».",
      "Without a diamond pickaxe you can't mine obsidian, so cast the portal in place: build a mould, pour lava into each frame cell and pour water over it. Ruined portals also hold obsidian. Enter the Nether to unlock the diamond pickaxe recipe and the Nether chapter.",
      [dim("minecraft:the_nether")], [unlock("diamond_pickaxe")], ["mild/diamonds"], icon="minecraft:obsidian"),
    Q("mild/south", 7, 0, "Дорога на юг", "The Road South",
      "Горячий остров лежит в 3500–4500 блоках к югу, в Незере это в 8 раз ближе. Иди на юг до места около x 0, z 500 (от −60 до 60 по x и от 440 до 560 по z). Портал, построенный там, выведет на горячий остров.",
      "The hot island lies 3500 to 4500 blocks south; in the Nether that is 8 times closer. Head south to around x 0, z 500 (x −60 to 60, z 440 to 560). A portal built there leads to the hot island.",
      [area("minecraft:the_nether", -60, 0, 440, 121, 256, 121)], [], ["mild/nether"],
      icon="minecraft:netherrack", shape="hexagon", size=1.5),
    # ---- ветка 2: ферма (вверх)
    Q("mild/breed", 1.75, -1, "Пополнение", "New Arrivals", "Размножь любых животных.", "Breed any two animals.",
      [adv("minecraft:husbandry/breed_an_animal")], [give("minecraft:stone_hoe")], ["mild/table"], icon="minecraft:wheat"),
    Q("mild/plant", 2.75, -2, "Первый урожай", "First Crop", "Посади любую культуру.", "Plant any crop.",
      [adv("minecraft:husbandry/plant_seed")], [xp(100)], ["mild/breed"], icon="minecraft:wheat_seeds"),
    Q("mild/campfire", 3, -3.25, "Ужин у костра", "Campfire Dinner",
      "Сложи костёр и пожарь на нём мясо. Дальше кухня: глава Farmer's Delight.",
      "Build a campfire and cook some meat on it. Next comes the kitchen: the Farmer's Delight chapter.",
      [item("minecraft:campfire"), adv("palestore:quest/has_cooked_meat")], [xp(50)], ["mild/plant"], icon="minecraft:campfire"),
    # ---- ветка 3: пещеры (вниз)
    Q("mild/coal", 2.5, 1.5, "Уголь", "Coal", "Добудь уголь.", "Mine some coal.",
      [item("minecraft:coal")], [xp(50)], ["mild/stone"]),
    Q("mild/torch", 3.25, 2.5, "Факелы", "Torches", "Сделай факелы. В награду откроется рецепт лестниц.",
      "Make some torches. The reward unlocks the ladder recipe.",
      [item("minecraft:torch")], [unlock("ladder"), xp(100)], ["mild/coal"]),
    Q("mild/caves", 3.25, 3.75, "Под землёй", "Underground", "Спустись в любой пещерный биом.", "Go down into any cave biome.",
      [biome("#c:is_underground")], [FIRE_RES], ["mild/torch"], icon="minecraft:pointed_dripstone"),
    # ---- ветка 4: Cold Sweat (вверх)
    Q("mild/huddle", 3, -1, "Поближе к огню", "Huddle Close",
      "Когда вокруг слишком холодно (ночью или высоко в горах), согрейся у тёплого блока, например у костра.",
      "When it gets too cold around you (at night or high in the mountains), warm up next to a warm block such as a campfire.",
      [adv("cold_sweat:huddle_close")], [choice("iron_tool")], ["mild/bed"], icon="minecraft:campfire"),
    Q("mild/lining", 4, -1.75, "Подкладка", "Lining Up", "Утепли любую часть брони на швейном столе.",
      "Insulate any piece of armour on a sewing table.",
      [adv("cold_sweat:insulate_armor")], [book("minecraft:unbreaking", 2)], ["mild/huddle"], icon="cold_sweat:sewing_table"),
    Q("mild/cozy", 4.25, -3, "Уютно", "Getting Cozy", "Поставь дымоход на бойлер или ледник.",
      "Put a smokestack on a boiler or an icebox.",
      [adv("cold_sweat:use_smokestack")], [give("minecraft:white_wool", 15)], ["mild/lining"], icon="cold_sweat:smokestack"),
    # ---- ветка 5: железо (вниз)
    Q("mild/bucket", 4, 1, "Ведро", "Bucket", "Сделай ведро. В награду случайная книга «Озарение» I–III.",
      "Make a bucket. The reward is a random Insight book, I to III.",
      [item("minecraft:bucket")], [rand("insight_book")], ["mild/iron"]),
    Q("mild/deflect", 4.5, 2, "Не сегодня", "Not Today", "Отрази стрелу щитом.", "Deflect an arrow with a shield.",
      [adv("minecraft:story/deflect_arrow")], [rand("efficiency_book")], ["mild/bucket"], icon="minecraft:shield"),
    Q("mild/legs", 5, 3.25, "Ноги в железе", "Iron Legs", "Сделай железные поножи и ботинки.", "Make iron leggings and boots.",
      [item("minecraft:iron_leggings"), item("minecraft:iron_boots")], [rand("protection_book")], ["mild/deflect"],
      icon="minecraft:iron_boots"),
    Q("mild/ready", 4.25, 4, "Готов к жаре", "Ready for the Heat", "Пещеры пройдены, железо надето. Возьми огнестойкость подольше.",
      "Caves explored, iron on. Take a longer fire resistance.",
      [check()], [LONG_FIRE_RES], ["mild/caves", "mild/legs"], icon="minecraft:magma_cream"),
    # ---- ветка 6: зачарования (вверх)
    Q("mild/enchanting", 5.5, -1, "Стол зачарований", "Enchanting Table", "Сделай стол зачарований.", "Make an enchanting table.",
      [item("minecraft:enchanting_table")], [give("minecraft:cooked_porkchop", 16), xp(150)], ["mild/diamonds"]),
    Q("mild/bookshelves", 6.25, -2, "Полная мощность", "Full Power",
      "Принеси 15 книжных полок (они не заберутся) и поставь их вокруг стола: так он даёт самые сильные зачарования.",
      "Bring 15 bookshelves (they are not taken) and place them around the table: that gives the strongest enchantments.",
      [item("minecraft:bookshelf", 15)], [book("minecraft:mending")], ["mild/enchanting"]),
    Q("mild/enchanter", 6.5, -3.25, "Чародей", "Enchanter", "Зачаруй предметы на столе 30 раз. Дальше глава Death List.",
      "Enchant items on the table 30 times. Next comes the Death List chapter.",
      [stat("minecraft:enchant_item", 30)], [], ["mild/bookshelves"], icon="minecraft:experience_bottle"),
], scale=1.5)


# ================================================================ 2. Незер (3.0, черновик Claude для Max)
# Открывается квестом 7 главы 1 («В Незер»): первый квест скрыт, пока тот не выполнен, остальные видны вместе с ним.
SHOWN_AFTER_7 = {"hide_until_deps_complete": True}
WITH_CHAPTER = {"hide_until_deps_visible": True}

table("fire_protection_book", "Книга «Огнеупорность»", "Fire Protection Book",
      [book("minecraft:fire_protection", n) for n in (1, 2, 3)])


def NQ(*args, **kw):
    kw.setdefault("extra", WITH_CHAPTER)
    return Q(*args, **kw)


chapter("nether", "minecraft:netherrack", "Незер", "The Nether",
        "Огонь, крепости и короткая дорога на юг: блок здесь равен восьми наверху.",
        "Fire, fortresses and the short way south: one block here is eight up top.", [
    # ---- прямой путь
    Q("nether/arrive", 0, 0, "Горячий приём", "A Warm Welcome",
      "Ты в Незере. Здесь один блок равен восьми в обычном мире, поэтому до горячего острова отсюда всего около 500 блоков на юг. Сначала осмотрись: рядом крепости, бастионы и обломки, без которых дальше не обойтись.",
      "You are in the Nether. One block here is eight in the Overworld, so the hot island is only about 500 blocks south. Look around first: fortresses, bastions and debris are close, and you will need them.",
      [check()], [xp(50), give("minecraft:golden_carrot", 8)], ["mild/nether"], icon="minecraft:netherrack",
      shape="hexagon", size=1.5, extra=SHOWN_AFTER_7),
    NQ("nether/pick", 1, 0, "Алмазная кирка", "Diamond Pickaxe", "Рецепт открыт. Скрафти алмазную кирку: без неё не добыть обломки и обсидиан.",
       "The recipe is open. Craft a diamond pickaxe: you need it for debris and obsidian.",
       [item("minecraft:diamond_pickaxe")], [rand("efficiency_book")], ["nether/arrive"]),
    NQ("nether/fortress", 2, 0, "Крепость", "Fortress", "Найди крепость Незера.", "Find a Nether fortress.",
       [structure("minecraft:fortress")], [xp(100), give("minecraft:golden_apple")], ["nether/pick"], icon="minecraft:nether_bricks"),
    NQ("nether/blaze", 3, 0, "Ифриты", "Blazes", "Убей 8 ифритов и собери 6 огненных стержней.", "Kill 8 blazes and collect 6 blaze rods.",
       [kill("minecraft:blaze", 8), item("minecraft:blaze_rod", 6)], [xp(150)], ["nether/fortress"], icon="minecraft:blaze_rod"),
    NQ("nether/brewing", 4, 0, "Зельеварка", "Brewing Stand", "Поставь варочную стойку. Адский нарост растёт в крепостях.",
       "Make a brewing stand. Nether wart grows in fortresses.",
       [item("minecraft:brewing_stand")], [give("minecraft:nether_wart", 8)], ["nether/blaze"]),
    NQ("nether/potion", 5, 0, "Своя аптечка", "Home Brew", "Свари любое зелье.", "Brew any potion.",
       [adv("minecraft:nether/brew_potion")], [xp(150), rand("fire_protection_book")], ["nether/brewing"], icon="minecraft:potion"),
    NQ("nether/ready", 6, 0, "Готов к югу", "Ready for the South",
       "Зелья, жаростойкая броня и дорога через Незер: можно идти на горячий остров.",
       "Potions, heatproof armour and a road through the Nether: time to head for the hot island.",
       [check()], [xp(300), give("minecraft:obsidian", 10)], ["nether/potion", "nether/hoglin_armor"],
       icon="minecraft:compass", shape="hexagon", size=1.5),
    # ---- вверх от 1: биомы
    NQ("nether/crimson", 0.25, -1.25, "Багровый лес", "Crimson Forest", "Здесь пасутся хоглины.", "Hoglins graze here.",
       [biome("minecraft:crimson_forest")], [xp(50)], ["nether/arrive"], icon="minecraft:crimson_fungus"),
    NQ("nether/warped", 0.5, -2.25, "Искажённый лес", "Warped Forest", "Здесь почти никто не нападает. Хорошее место для лагеря.",
       "Hardly anything attacks you here. A good spot for a camp.",
       [biome("minecraft:warped_forest")], [xp(50)], ["nether/crimson"], icon="minecraft:warped_fungus"),
    NQ("nether/soul", 0.75, -3.25, "Долина душ", "Soul Sand Valley", "Синий огонь и песок душ.", "Blue fire and soul sand.",
       [biome("minecraft:soul_sand_valley")], [xp(50)], ["nether/warped"], icon="minecraft:soul_sand"),
    NQ("nether/basalt", 1, -4.25, "Базальтовые дельты", "Basalt Deltas", "Самое неприятное место в Незере.", "The nastiest place in the Nether.",
       [biome("minecraft:basalt_deltas")], [xp(50)], ["nether/soul"], icon="minecraft:basalt"),
    NQ("nether/tourist", 1.75, -5, "Турист", "Hot Tourist", "Побывай во всех биомах Незера.", "Visit every Nether biome.",
       [adv("minecraft:nether/explore_nether")], [xp(300), rand("protection_book")], ["nether/basalt"], icon="minecraft:map"),
    # ---- вверх от 3: бастион
    NQ("nether/bastion", 2.25, -1.25, "Бастион", "Bastion", "Опасно, но там золото, шаблоны и обломки.", "Dangerous, but full of gold, templates and debris.",
       [structure("minecraft:bastion_remnant")], [xp(100)], ["nether/fortress"], icon="minecraft:gilded_blackstone"),
    NQ("nether/barter", 2.5, -2.25, "Блестяшка", "Oh Shiny", "Отвлеки пиглина золотом.", "Distract a piglin with gold.",
       [adv("minecraft:nether/distract_piglin")], [give("minecraft:gold_ingot", 8)], ["nether/bastion"], icon="minecraft:gold_ingot"),
    NQ("nether/template", 2.75, -3.25, "Шаблон улучшения", "Upgrade Template", "Шаблон незеритового улучшения лежит в сундуках бастиона.",
       "The netherite upgrade template is in bastion chests.",
       [item("minecraft:netherite_upgrade_smithing_template")], [xp(200)], ["nether/barter"]),
    # ---- вверх от 4: иссушитель
    NQ("nether/skulls", 3.75, -1.25, "Черепа", "Skulls", "Добудь 3 черепа скелетов-иссушителей.", "Collect 3 wither skeleton skulls.",
       [item("minecraft:wither_skeleton_skull", 3)], [xp(200)], ["nether/blaze"]),
    NQ("nether/wither", 4.25, -2.25, "Иссушитель", "The Wither", "Призови и победи Иссушителя. Подальше от дома.",
       "Summon and defeat the Wither. Far from home.",
       [kill("minecraft:wither")], [xp(1000), give("minecraft:golden_apple", 2)], ["nether/skulls"], icon="minecraft:nether_star"),
    NQ("nether/beacon", 4.75, -3.25, "Маяк", "Beacon", "Звезда Незера и обсидиан.", "A nether star and obsidian.",
       [item("minecraft:beacon")], [xp(300)], ["nether/wither"]),
    # ---- вниз от 2: незерит
    NQ("nether/debris", 1.25, 1.25, "Древние обломки", "Ancient Debris", "Глубоко в Незере, ниже y 20. Нужна алмазная кирка.",
       "Deep in the Nether, below y 20. Needs a diamond pickaxe.",
       [item("minecraft:ancient_debris", 4)], [xp(150)], ["nether/pick"]),
    NQ("nether/netherite", 1.75, 2.25, "Незерит", "Netherite", "Слиток из обломков и золота.", "An ingot from scrap and gold.",
       [item("minecraft:netherite_ingot")], [xp(200)], ["nether/debris"]),
    NQ("nether/netherite_pick", 2.25, 3.25, "Незеритовая кирка", "Netherite Pickaxe", "Улучши алмазную кирку на кузнечном столе.",
       "Upgrade a diamond pickaxe at a smithing table.",
       [item("minecraft:netherite_pickaxe")], [xp(300), book("minecraft:mending")], ["nether/netherite", "nether/template"]),
    # ---- вниз от 5: жаростойкая броня (Cold Sweat)
    NQ("nether/hoglin", 4, 1.25, "Шкура хоглина", "Hoglin Hide", "Хоглины пасутся в багровом лесу.", "Hoglins graze in the crimson forest.",
       [item("cold_sweat:hoglin_hide", 6)], [xp(75)], ["nether/brewing"]),
    NQ("nether/hoglin_armor", 4.75, 2.25, "Жаростойкий доспех", "Heatproof Armour",
       "Нагрудник из шкуры хоглина держит жару. На горячем острове без него тяжело.",
       "A hoglin hide chestplate keeps the heat out. The hot island is hard without one.",
       [item("cold_sweat:hoglin_chestplate")], [xp(150)], ["nether/hoglin"]),
    NQ("nether/lamp", 5.5, 3.25, "Лампа душ", "Soulspring Lamp", "Лампа на огне душ держит температуру вокруг тебя. Пригодится и на юге, и на севере.",
       "A soul-fire lamp keeps the temperature around you steady. Useful both south and north.",
       [item("cold_sweat:soulspring_lamp")], [xp(150)], ["nether/hoglin_armor"]),
], scale=1.5)


# ================================================================ 3. Горячий остров
chapter("hot", "minecraft:sand", "Горячий остров", "The Hot Island",
        "Пустыни на востоке, джунгли на западе. И первые порождения хаоса.",
        "Deserts to the east, jungle to the west. And the first spawn of chaos.", [
    Q("hot/arrive", 0, 0, "Пески юга", "Southern Sands", "Выйди из портала и найди пустыню. Пей воду и следи за градусником.",
      "Step out of the portal and find the desert. Drink water and watch your thermometer.",
      [biome("minecraft:desert")], [xp(200)], ["nether/south"], icon="minecraft:sand", shape="hexagon", size=1.5),
    Q("hot/jungle", -2, -2, "Джунгли", "Jungle", "Тёплый и влажный запад острова.", "The warm, wet west of the island.",
      [biome("#minecraft:is_jungle")], [xp(100)], ["hot/arrive"], icon="minecraft:jungle_sapling"),
    Q("hot/badlands", -2, 2, "Пустоши", "Badlands", "Красные скалы на востоке.", "Red cliffs in the east.",
      [biome("#minecraft:is_badlands")], [xp(100)], ["hot/arrive"], icon="minecraft:red_sand"),
    Q("hot/pyramid", -4, 2, "Пирамида", "Pyramid", "Найди пустынную пирамиду. Осторожно с полом.",
      "Find a desert pyramid. Mind the floor.", [structure("minecraft:desert_pyramid")], [xp(120)], ["hot/badlands"], icon="minecraft:chiseled_sandstone"),
    Q("hot/temple", -4, -2, "Храм в джунглях", "Jungle Temple", "Найди храм в джунглях.", "Find a jungle temple.",
      [structure("minecraft:jungle_pyramid")], [xp(120)], ["hot/jungle"], icon="minecraft:mossy_cobblestone"),
    Q("hot/icebox", 2, 2, "Холодильник", "Icebox", "Охлаждает дом, когда снаружи жара.", "Cools your home when it's scorching outside.",
      [item("cold_sweat:icebox")], [xp(100)], ["hot/arrive"]),
    Q("hot/chameleon", 2, 4, "Хамелеон", "Chameleon", "Броня из линьки хамелеона подстраивается под жару и холод.",
      "Armour from a chameleon's molt adapts to heat and cold.",
      [item("cold_sweat:chameleon_molt", 4), item("cold_sweat:chameleon_chestplate")], [xp(150)], ["hot/icebox"], icon="cold_sweat:chameleon_chestplate"),
    # Born in Chaos
    Q("hot/chaos1", 2, -2, "Порождения хаоса", "Spawn of Chaos", "На стартовом острове их не было. Здесь они есть.",
      "There were none on the starter island. Here there are.",
      [kill("born_in_chaos_v1:door_knight", 3), kill("born_in_chaos_v1:bonescaller", 2)], [xp(150)], ["hot/arrive"], icon="born_in_chaos_v1:dark_metal_nugget"),
    Q("hot/fallen", 4, -4, "Падший рыцарь", "Fallen Knight", "Победи падшего рыцаря хаоса.", "Defeat a Fallen Chaos Knight.",
      [kill("born_in_chaos_v1:fallen_chaos_knight")], [xp(250)], ["hot/chaos1"], icon="born_in_chaos_v1:darkwarblade"),
    Q("hot/darkmetal", 4, -2, "Тёмный металл", "Dark Metal", "Собери слитки тёмного металла и скуй нагрудник.",
      "Gather dark metal ingots and forge a chestplate.",
      [item("born_in_chaos_v1:dark_metal_ingot", 8), item("born_in_chaos_v1:dark_metal_armor_chestplate")], [xp(200)], ["hot/chaos1"], icon="born_in_chaos_v1:dark_metal_ingot"),
    # Create, brass
    Q("hot/burner", 4, 0, "Горелка ифрита", "Blaze Burner", "Нужна, чтобы плавить латунь.", "You need it to make brass.",
      [item("create:blaze_burner")], [xp(100)], ["hot/arrive", "nether/blaze"]),
    Q("hot/brass", 6, 0, "Латунь", "Brass", "Медь и цинк в нагретой чаше.", "Copper and zinc in a heated basin.",
      [item("create:brass_ingot", 16)], [xp(150)], ["hot/burner"]),
    Q("hot/mechanism", 8, 0, "Точный механизм", "Precision Mechanism", "Сердце сложных машин Create.", "The heart of Create's advanced machines.",
      [item("create:precision_mechanism", 2)], [xp(200)], ["hot/brass"]),
    Q("hot/steam", 10, 0, "Пар", "Steam", "Паровой двигатель над резервуаром с горелкой.", "A steam engine on a fluid tank with a burner.",
      [item("create:steam_engine"), item("create:fluid_tank", 4)], [xp(250)], ["hot/mechanism"]),
    # Create Crafts & Additions
    Q("hot/motor", 10, 2, "Электромотор", "Electric Motor", "Create Additions превращает электричество во вращение.",
      "Create Additions turns electricity into rotation.",
      [item("createaddition:electric_motor")], [xp(150)], ["hot/steam"]),
    Q("hot/alternator", 12, 2, "Генератор", "Alternator", "И обратно: вращение в электричество.", "And back: rotation into electricity.",
      [item("createaddition:alternator"), item("createaddition:modular_accumulator")], [xp(200)], ["hot/motor"]),
    # Oritech, basics
    Q("hot/generator", 12, 0, "Oritech", "Oritech", "Первый генератор Oritech.", "Your first Oritech generator.",
      [item("oritech:basic_generator_block")], [xp(150)], ["hot/steam"]),
    Q("hot/pulverizer", 14, 0, "Измельчитель и печь", "Pulverizer and Furnace", "Удвой выход руды.", "Double your ore.",
      [item("oritech:pulverizer_block"), item("oritech:powered_furnace_block")], [xp(200)], ["hot/generator"]),
    Q("hot/steel", 14, -2, "Сталь", "Steel", "Литейная Oritech делает сталь.", "Oritech's foundry makes steel.",
      [item("oritech:foundry_block"), item("oritech:steel_ingot", 16)], [xp(250)], ["hot/pulverizer"], icon="oritech:steel_ingot"),
    # the way to the End
    Q("hot/eyes", 6, 4, "Око Эндера", "Eye of Ender", "Двенадцать око Эндера. Они ведут к крепости.",
      "Twelve eyes of ender. They lead to the stronghold.",
      [item("minecraft:ender_eye", 12)], [xp(200)], ["hot/arrive", "nether/blaze"]),
    Q("hot/stronghold", 8, 4, "Крепость Края", "Stronghold", "Все три крепости стоят на горячем острове, на полпути от центра к берегу.",
      "All three strongholds are on the hot island, half way out from its centre.",
      [structure("minecraft:stronghold")], [xp(300)], ["hot/eyes"], icon="minecraft:end_portal_frame", shape="hexagon", size=1.5),
])

# ================================================================ 4. Энд
chapter("end", "minecraft:end_stone", "Край", "The End", "Дракон стережёт крылья, без которых на север не попасть.",
        "The dragon guards the wings you need to reach the north.", [
    Q("end/enter", 0, 0, "Конец?", "The End?", "Войди в портал Края.", "Enter the End portal.",
      [dim("minecraft:the_end")], [xp(300)], ["hot/stronghold"], icon="minecraft:end_portal_frame", shape="hexagon", size=1.5),
    Q("end/dragon", 2, 0, "Дракон", "The Dragon", "Убей Эндер-дракона.", "Slay the Ender Dragon.",
      [kill("minecraft:ender_dragon")], [xp(1000), give("minecraft:golden_apple", 2)], ["end/enter"], icon="minecraft:dragon_head", shape="gear", size=2.0),
    Q("end/breath", 2, 2, "Дыхание дракона", "Dragon's Breath", "Набери его в бутылки.", "Collect it in bottles.",
      [item("minecraft:dragon_breath", 4)], [xp(100)], ["end/dragon"]),
    Q("end/city", 4, 0, "Город Края", "End City", "Найди город Края за внешними островами.", "Find an End city past the outer islands.",
      [structure("minecraft:end_city")], [xp(300)], ["end/dragon"], icon="minecraft:purpur_block"),
    Q("end/shulker", 4, 2, "Шалкеры", "Shulkers", "Панцири шалкеров для ящиков.", "Shulker shells for boxes.",
      [item("minecraft:shulker_shell", 4)], [xp(200)], ["end/city"]),
    Q("end/elytra", 6, 0, "Элитры", "Elytra", "Единственный путь на холодный остров. Портал из Незера туда не откроется.",
      "The only way to the cold island. A portal from the Nether won't open there.",
      [item("minecraft:elytra")], [xp(500)], ["end/city"], shape="hexagon", size=1.5),
    Q("end/rockets", 6, 2, "Фейерверки", "Rockets", "Перелёт долгий. Возьми много ракет.", "It's a long flight. Bring plenty of rockets.",
      [item("minecraft:firework_rocket", 64)], [xp(150), give("minecraft:firework_rocket", 32)], ["end/elytra"]),
])

# ================================================================ 5. Холодный остров
ABOVE_CLOUDS = area("minecraft:overworld", -30000000, 500, -30000000, 60000000, 1500, 60000000)
chapter("cold", "minecraft:snow_block", "Холодный остров", "The Cold Island",
        "Самый опасный остров. Только на крыльях и только для тех, кто готов.",
        "The most dangerous island. Only on wings, and only for those who are ready.", [
    Q("cold/clouds", 0, 0, "Над облаками", "Above the Clouds", "Поднимись выше 500 блоков: там нет ни тумана, ни яда. Внизу море облаков, острова видны как дыры в нём.",
      "Climb above 500 blocks: no fog and no poison up there. Below lies a sea of clouds with the islands as holes in it.",
      [ABOVE_CLOUDS], [xp(300)], ["end/elytra"], icon="minecraft:feather", shape="hexagon", size=1.5),
    Q("cold/arrive", 2, 0, "Северный берег", "Northern Shore", "Лети на север (ось Z в минус) примерно 4000 блоков и сядь на снежной равнине.",
      "Fly about 4000 blocks north (negative Z) and land on the snowy plains.",
      [biome("minecraft:snowy_plains")], [xp(500)], ["cold/clouds"], icon="minecraft:snowball", shape="hexagon", size=1.5),
    Q("cold/spikes", 2, -2, "Ледяные шпили", "Ice Spikes", "Найди ледяные шпили.", "Find the ice spikes.",
      [biome("minecraft:ice_spikes")], [xp(200)], ["cold/arrive"], icon="minecraft:packed_ice"),
    Q("cold/fur", 4, 2, "Козий мех", "Goat Fur", "Меховая броня держит тепло.", "Fur armour keeps you warm.",
      [item("cold_sweat:goat_fur", 6), item("cold_sweat:goat_fur_chestplate")], [xp(200)], ["cold/arrive"], icon="cold_sweat:goat_fur_chestplate"),
    Q("cold/hearth", 6, 2, "Очаг", "Hearth", "Очаг греет весь дом.", "A hearth warms your whole home.",
      [item("cold_sweat:hearth")], [xp(250)], ["cold/fur"]),
    Q("cold/lamp", 8, 2, "Фонарь душ", "Soulspring Lamp", "Тепло, которое можно носить с собой.", "Warmth you can carry around.",
      [item("cold_sweat:soulspring_lamp")], [xp(250)], ["cold/hearth"]),
    # Oritech, only up here
    Q("cold/ores", 4, -2, "Северные руды", "Northern Ores", "Никель, платина и уран есть только на холодном острове.",
      "Nickel, platinum and uranium exist only on the cold island.",
      [item("oritech:raw_nickel", 16), item("oritech:raw_platinum", 8)], [xp(300)], ["cold/arrive", "hot/steel"], icon="oritech:raw_nickel"),
    Q("cold/uranium", 6, -2, "Уран", "Uranium", "Осторожно, он пригодится для реактора.", "Careful, you'll want it for a reactor.",
      [item("oritech:raw_uranium", 8)], [xp(300)], ["cold/ores"]),
    Q("cold/laser", 6, -4, "Лазер", "Laser Arm", "Лазерная рука Oritech добывает на расстоянии.", "Oritech's laser arm mines from afar.",
      [item("oritech:laser_arm_block")], [xp(400)], ["cold/ores"]),
    Q("cold/drill", 8, -4, "Глубинный бур", "Deep Drill", "Бурит рудные жилы бесконечно.", "Drills ore nodes forever.",
      [item("oritech:deep_drill_block")], [xp(500)], ["cold/laser"]),
    Q("cold/jetpack", 8, -2, "Реактивный ранец", "Jetpack", "Ранец с элитрами: летать между островами станет проще.",
      "A jetpack with elytra makes flying between islands easier.",
      [item("oritech:jetpack_elytra")], [xp(500)], ["cold/uranium"]),
    # Born in Chaos bosses, only up here
    Q("cold/tower", 10, 0, "Тёмная башня", "Dark Tower", "Тёмные башни стоят только на севере.", "Dark towers stand only in the north.",
      [structure("born_in_chaos_v1:dark_tower_taiga")], [xp(300)], ["cold/arrive"], icon="born_in_chaos_v1:black_argillite_brick"),
    Q("cold/hounds", 12, -2, "Вожак гончих", "Hound Leader", "Победи вожака ужасных гончих.", "Defeat the Dire Hound Leader.",
      [kill("born_in_chaos_v1:dire_hound_leader")], [xp(500)], ["cold/tower"], icon="born_in_chaos_v1:fangofthe_hound_leader"),
    Q("cold/spider", 12, 0, "Матерь пауков", "Mother Spider", "Победи Матерь пауков.", "Defeat the Mother Spider.",
      [kill("born_in_chaos_v1:mother_spider")], [xp(500)], ["cold/tower"], icon="born_in_chaos_v1:spider_mandible"),
    Q("cold/missioner", 12, 2, "Миссионер", "Missioner", "Победи Миссионера.", "Defeat the Missioner.",
      [kill("born_in_chaos_v1:missioner")], [xp(500)], ["cold/tower"], icon="born_in_chaos_v1:missionary_hat_helmet"),
    Q("cold/stalker", 14, -2, "Кошмарный сталкер", "Nightmare Stalker", "Он охотится в темноте.", "It hunts in the dark.",
      [kill("born_in_chaos_v1:nightmare_stalker")], [xp(750)], ["cold/hounds"], icon="born_in_chaos_v1:nightmare_claw"),
    Q("cold/lifestealer", 14, 0, "Похититель жизни", "Lifestealer", "Победи Похитителя жизни.", "Defeat the Lifestealer.",
      [kill("born_in_chaos_v1:lifestealer")], [xp(750)], ["cold/spider"], icon="born_in_chaos_v1:lifestealer_bone"),
    Q("cold/krampus", 14, 2, "Крампус", "Krampus", "Победи Крампуса.", "Defeat Krampus.",
      [kill("born_in_chaos_v1:krampus")], [xp(750)], ["cold/missioner"], icon="born_in_chaos_v1:krampus_horn"),
    Q("cold/elixir", 14, 4, "Превращающий эликсир", "Transmuting Elixir", "Этим эликсиром будят Лорда Тыквоголового. Работает только на севере.",
      "This elixir wakes Lord Pumpkinhead. It only works in the north.",
      [item("born_in_chaos_v1:transmuting_elixir"), item("born_in_chaos_v1:infernal_evil_pumpkin")], [xp(300)], ["cold/krampus"]),
    Q("cold/pumpkinhead", 16, 2, "Лорд Тыквоголовый", "Lord Pumpkinhead", "Полей эликсиром адскую тыкву и победи Лорда.",
      "Pour the elixir on an infernal pumpkin and defeat the Lord.",
      [kill("born_in_chaos_v1:lord_pumpkinhead")], [xp(1000)], ["cold/elixir"], icon="born_in_chaos_v1:lord_pumpkinheads_hat_helmet"),
    Q("cold/final", 18, 0, "Хозяин Серости", "Master of the Pale", "Ты прошёл все три острова и одолел всех, кто прятался на севере.",
      "You crossed all three islands and beat everything that hid in the north.",
      [check()], [xp(3000), give("minecraft:nether_star"), give("minecraft:netherite_ingot", 2)],
      ["cold/stalker", "cold/lifestealer", "cold/pumpkinhead"], icon="minecraft:nether_star", shape="gear", size=2.5),
])

# ================================================================ 6. Зачарования (side)
chapter("enchant", "minecraft:enchanting_table", "Зачарования", "Enchanting",
        "Pale Store: зачарования стоят 125, 250 и 500 очков опыта. Наковальня бесплатна.",
        "Pale Store: enchanting costs 125, 250 and 500 XP points. The anvil is free.", [
    Q("enchant/table", 0, 0, "Стол зачарований", "Enchanting Table", "Первый вариант на столе стоит 125 очков опыта и не требует полок.",
      "The first option costs 125 XP points and needs no bookshelves.",
      [item("minecraft:enchanting_table")], [xp(125)], ["mild/diamonds"], shape="hexagon", size=1.5),
    Q("enchant/shelves", 2, -1, "Библиотека", "Library", "8 полок открывают второй вариант (250 очков), 15 полок третий (500 очков).",
      "8 bookshelves unlock the second option (250 points), 15 the third (500 points).",
      [item("minecraft:bookshelf", 15)], [xp(150)], ["enchant/table"]),
    Q("enchant/first", 2, 1, "Первые чары", "First Enchantment", "Зачаруй любой предмет.", "Enchant any item.",
      [adv("minecraft:story/enchant_item")], [xp(100)], ["enchant/table"], icon="minecraft:enchanted_book"),
    Q("enchant/anvil", 4, 1, "Наковальня", "Anvil", "В этой сборке наковальня не берёт опыт и не бывает «слишком дорого».",
      "In this pack the anvil takes no XP and is never \"too expensive\".",
      [item("minecraft:anvil")], [xp(100)], ["enchant/first"]),
    Q("enchant/smelting", 6, -2, "Переплавка", "Smelting", "Добытые блоки сразу выпадают переплавленными. Держи книгу.",
      "Mined blocks drop already smelted. Here's a book.",
      [item("minecraft:raw_iron", 32)], [book("paleenchants:smelting")], ["enchant/anvil"], icon="minecraft:raw_iron"),
    Q("enchant/smashing", 6, -1, "Дробление", "Smashing", "Руда раскалывается в дроблёную руду Create.",
      "Ore cracks into Create's crushed ore.",
      [item("create:crushed_raw_iron", 16)], [book("paleenchants:smashing")], ["enchant/anvil", "mild/press"], icon="create:crushed_raw_iron"),
    Q("enchant/insight", 6, 0, "Озарение", "Insight", "Больше опыта с мобов и блоков.", "More XP from mobs and blocks.",
      [item("minecraft:lapis_lazuli", 64)], [book("paleenchants:insight")], ["enchant/anvil"], icon="minecraft:lapis_lazuli"),
    Q("enchant/leech", 6, 1, "Пиявка", "Leech", "Убийство моба лечит тебя.", "Killing a mob heals you.",
      [kill("minecraft:zombie", 30)], [book("paleenchants:leech")], ["enchant/anvil"], icon="minecraft:rotten_flesh"),
    Q("enchant/vorpal", 6, 2, "Обезглавливание", "Vorpal", "Шанс сокрушительного удара и чужой головы.",
      "A chance of a crushing blow and of taking a head.",
      [kill("minecraft:skeleton", 30)], [book("paleenchants:vorpal")], ["enchant/anvil", "hot/arrive"], icon="minecraft:skeleton_skull"),
    Q("enchant/soulbound", 8, 0, "Связь с душой", "Soulbound", "Предмет остаётся с тобой после смерти. Самая ценная книга, её надо заслужить.",
      "The item stays with you when you die. The most precious book; you have to earn it.",
      [item("minecraft:totem_of_undying")], [book("paleenchants:soulbound")], ["enchant/anvil", "hot/fallen"], icon="minecraft:totem_of_undying", shape="hexagon", size=1.5),
])



# ================================================================ Version 2.0
# New quests for the mods of the 1.69 pack. Old keys stay as they were, so progress carries over.
def extend(key, quests):
    for c in CHAPTERS:
        if c["key"] == key:
            c["quests"].extend(quests)
            return
    raise KeyError(key)


# ---------------------------------------------------------------- Кухня (Farmer's Delight)
chapter("kitchen", "farmersdelight:cooking_pot", "Кухня", "The Kitchen",
        "Farmer's Delight: горячий суп греет на севере, мороженое охлаждает на юге.",
        "Farmer's Delight: hot soup keeps you warm in the north, ice cream keeps you cool in the south.", [
    Q("kitchen/knife", 0, 0, "Нож", "Knife", "Нож режет траву на солому, а мясо на куски. С ним начинается кухня. AppleSkin покажет, сколько сытости даёт каждое блюдо.",
      "A knife cuts grass into straw and meat into cuts. The kitchen starts here. AppleSkin shows how filling each dish is.",
      [item("farmersdelight:flint_knife")], [xp(30)], ["mild/table"], shape="hexagon", size=1.5),
    Q("kitchen/straw", 2, -2, "Солома", "Straw", "Срежь ножом высокую траву.", "Cut tall grass with a knife.",
      [item("farmersdelight:straw", 16)], [xp(30)], ["kitchen/knife"]),
    Q("kitchen/canvas", 4, -2, "Холст", "Canvas", "Из соломы ткут холст.", "Straw is woven into canvas.",
      [item("farmersdelight:canvas", 4)], [xp(30)], ["kitchen/straw"]),
    Q("kitchen/wild", 2, 2, "Дикие овощи", "Wild Vegetables", "Капуста растёт у берегов, лук на равнинах. Собери оба.",
      "Cabbages grow by the shore, onions on the plains. Gather both.",
      [item("farmersdelight:cabbage", 4), item("farmersdelight:onion", 4)], [xp(50)], ["kitchen/knife"], icon="farmersdelight:cabbage"),
    Q("kitchen/stove", 2, 0, "Плита", "Stove", "Плита греет всё, что стоит на ней.", "A stove heats whatever stands on it.",
      [item("farmersdelight:stove")], [xp(40)], ["kitchen/knife"]),
    Q("kitchen/pot", 4, 0, "Котёл", "Cooking Pot", "Поставь котёл на плиту. В нём варят супы и рагу.", "Put the pot on the stove. Soups and stews cook in it.",
      [item("farmersdelight:cooking_pot")], [xp(60)], ["kitchen/stove"], shape="hexagon", size=1.25),
    Q("kitchen/board", 4, 2, "Разделочная доска", "Cutting Board", "Кладёшь продукт, бьёшь ножом, получаешь нарезку.", "Put food on it, hit it with a knife, get slices.",
      [item("farmersdelight:cutting_board")], [xp(40)], ["kitchen/wild"]),
    Q("kitchen/skillet", 6, 2, "Сковорода", "Skillet", "На сковороде жарят прямо на огне. Ею ещё и бьют.", "A skillet fries right over the fire. It hits hard, too.",
      [item("farmersdelight:skillet")], [xp(40)], ["kitchen/board"]),
    Q("kitchen/soup", 6, 0, "Горячий суп", "Hot Soup", "Овощной суп. Тёплая еда в этой сборке греет тело на минуту (Cold Sweat).",
      "Vegetable soup. Warm food in this pack warms your body for a minute (Cold Sweat).",
      [item("farmersdelight:vegetable_soup", 4)], [xp(80)], ["kitchen/pot", "kitchen/wild"]),
    Q("kitchen/stew", 8, 0, "Рагу", "Stew", "Говяжье рагу сытнее и тоже греет.", "Beef stew is more filling and warms you too.",
      [item("farmersdelight:beef_stew", 4)], [xp(80)], ["kitchen/soup"]),
    Q("kitchen/compost", 4, -4, "Компост", "Compost", "Органический компост превращает землю в плодородную почву.", "Organic compost turns dirt into rich soil.",
      [item("farmersdelight:organic_compost"), item("farmersdelight:rich_soil", 4)], [xp(80)], ["kitchen/canvas"]),
    Q("kitchen/rice", 6, -2, "Рис", "Rice", "Дикий рис растёт в болотах и джунглях. Его сажают в воду.", "Wild rice grows in swamps and jungles. Plant it in water.",
      [item("farmersdelight:rice", 16)], [xp(80)], ["kitchen/knife", "mild/swamp"]),
    Q("kitchen/tomato", 8, -2, "Помидоры", "Tomatoes", "Дикие помидоры растут только в жарких краях. Это горячий остров.", "Wild tomatoes only grow where it's hot. That means the hot island.",
      [item("farmersdelight:tomato", 8)], [xp(120)], ["kitchen/knife", "hot/arrive"]),
    Q("kitchen/pasta", 10, -2, "Паста с фрикадельками", "Pasta with Meatballs", "Тесто, томатный соус и фарш.", "Dough, tomato sauce and minced beef.",
      [item("farmersdelight:pasta_with_meatballs", 2)], [xp(150)], ["kitchen/tomato", "kitchen/pot"]),
    Q("kitchen/popsicle", 10, 2, "Эскимо", "Melon Popsicle", "Холодная еда охлаждает. На юге это спасение.", "Cold food cools you down. A lifesaver in the south.",
      [item("farmersdelight:melon_popsicle", 4)], [xp(100)], ["kitchen/board", "hot/arrive"]),
    Q("kitchen/cocoa", 10, 0, "Горячий шоколад", "Hot Cocoa", "Греет сильнее всего. Возьми с собой на север.", "The warmest drink there is. Take some north.",
      [item("farmersdelight:hot_cocoa", 4)], [xp(120)], ["kitchen/stew", "hot/jungle"]),
    Q("kitchen/feast", 12, 0, "Пир", "Feast", "Поставь на стол целое блюдо: жареную курицу, пастуший пирог или окорок.",
      "Put a whole dish on the table: roast chicken, shepherd's pie or a glazed ham.",
      [adv("farmersdelight:main/place_feast")], [xp(150)], ["kitchen/stew"], icon="farmersdelight:roast_chicken_block"),
    Q("kitchen/netherite_knife", 12, -2, "Незеритовый нож", "Netherite Knife", "Лучший нож.", "The best knife there is.",
      [item("farmersdelight:netherite_knife")], [xp(200)], ["kitchen/pasta", "nether/debris"]),
    Q("kitchen/chef", 14, 0, "Шеф-повар", "Master Chef", "Приготовь и съешь все блюда из списка достижения Farmer's Delight.",
      "Cook and eat every dish on Farmer's Delight's advancement list.",
      [adv("farmersdelight:main/master_chef")], [xp(500), give("farmersdelight:honey_glazed_ham_block")], ["kitchen/feast", "kitchen/pasta", "kitchen/cocoa"],
      icon="farmersdelight:skillet", shape="gear", size=1.75),
])

# ---------------------------------------------------------------- Археология (Better Archeology)
chapter("archeology", "minecraft:brush", "Археология", "Archeology",
        "Под песком и землёй лежат остатки тех, кто жил здесь до Бледноты.",
        "Under the sand and soil lie the remains of those who lived here before the Pale.", [
    Q("arch/brush", 0, 0, "Кисть", "Brush", "Кистью счищают подозрительный песок и гравий.", "A brush sweeps suspicious sand and gravel.",
      [item("minecraft:brush")], [xp(40)], ["mild/copper"], shape="hexagon", size=1.5),
    Q("arch/trail", 2, -2, "Тропа", "Trail Ruins", "Найди руины тропы и раскопай черепок.", "Find trail ruins and dig up a sherd.",
      [structure("minecraft:trail_ruins"), adv("minecraft:adventure/salvage_sherd")], [xp(100)], ["arch/brush"], icon="minecraft:suspicious_gravel"),
    Q("arch/iron_brush", 2, 0, "Железная кисть", "Iron Brush", "Быстрее и крепче обычной.", "Faster and sturdier than the plain one.",
      [item("betterarcheology:iron_brush")], [xp(60)], ["arch/brush"]),
    Q("arch/diamond_brush", 4, 0, "Алмазная кисть", "Diamond Brush", "Ещё быстрее.", "Faster still.",
      [item("betterarcheology:diamond_brush")], [xp(120)], ["arch/iron_brush", "mild/diamonds"]),
    Q("arch/netherite_brush", 6, 0, "Незеритовая кисть", "Netherite Brush", "Самая быстрая кисть.", "The fastest brush.",
      [item("betterarcheology:netherite_brush")], [xp(200)], ["arch/diamond_brush", "nether/debris"]),
    Q("arch/fossil", 2, 2, "Окаменелость", "Fossil", "Раскопай любую окаменелость.", "Dig up any fossil.",
      [adv("betterarcheology:fossil_found")], [xp(100)], ["arch/brush"], icon="betterarcheology:fossiliferous_dirt"),
    Q("arch/shards", 4, 2, "Осколки артефактов", "Artifact Shards", "Их находят в раскопках. Из осколков собирают артефакт.", "Found while digging. Shards make up an artifact.",
      [item("betterarcheology:artifact_shards", 4)], [xp(100)], ["arch/fossil"]),
    Q("arch/table", 6, 2, "Стол археолога", "Archeology Table", "На нём опознают артефакты.", "Artifacts are identified on it.",
      [item("betterarcheology:archeology_table")], [xp(100)], ["arch/shards"]),
    Q("arch/unidentified", 8, 2, "Неопознанный артефакт", "Unidentified Artifact", "Собери артефакт из осколков.", "Put an artifact together from shards.",
      [item("betterarcheology:unidentified_artifact")], [xp(150)], ["arch/table"]),
    Q("arch/identified", 10, 2, "Опознать артефакт", "Identify an Artifact",
      "Положи неопознанный артефакт на стол археолога. Он превратится в книгу с одним из трёх древних зачарований: Парящий ветер, Пробивающий удар или Проходка.",
      "Put an unidentified artifact on the archeology table. It turns into a book with one of three ancient enchantments: Soaring Winds, Penetrating Strike or Tunneling.",
      [check()], [xp(250)], ["arch/unidentified"], icon="minecraft:enchanted_book", shape="hexagon", size=1.25),
    Q("arch/vase", 4, -2, "Ваза", "Vase", "Разбей вазу с добычей.", "Break a loot vase.",
      [adv("betterarcheology:loot_vase_broken")], [xp(60)], ["arch/trail"], icon="betterarcheology:loot_vase"),
    Q("arch/stonehenge", 0, -4, "Каменный круг", "Stone Circle", "Найди каменный круг на равнинах.", "Find a stone circle on the plains.",
      [structure("betterarcheology:stonehenge_grassy")], [xp(120)], ["arch/brush"], icon="minecraft:mossy_stone_bricks"),
    Q("arch/tumulus", 2, -4, "Курган", "Burial Mound", "Найди травяной курган.", "Find a grassy burial mound.",
      [structure("betterarcheology:tumulus_grassy")], [xp(120)], ["arch/brush"], icon="minecraft:coarse_dirt"),
    Q("arch/grave", 4, -4, "Могила жителя", "Villager Grave", "Кого-то здесь похоронили давным-давно.", "Someone was buried here long ago.",
      [structure("betterarcheology:villager_grave")], [xp(100)], ["arch/brush"], icon="minecraft:cobblestone_wall"),
    Q("arch/mott", 6, -4, "Мотт", "Motte", "Старое укрепление на холме в лесу.", "An old fort on a mound in the woods.",
      [structure("betterarcheology:mott")], [xp(120)], ["arch/brush"], icon="minecraft:oak_fence"),
    Q("arch/catacombs", 8, -4, "Катакомбы", "Catacombs", "Подземные ходы под тайгой или джунглями.", "Tunnels under the taiga or the jungle.",
      [structure("betterarcheology:catacombs")], [xp(200)], ["arch/brush"], icon="betterarcheology:rotten_planks"),
    Q("arch/obelisk", 10, -4, "Обелиск", "Desert Obelisk", "Обелиск стоит в пустыне горячего острова.", "The obelisk stands in the desert of the hot island.",
      [structure("betterarcheology:desert_obelisk")], [xp(200)], ["arch/brush", "hot/arrive"], icon="minecraft:sandstone"),
    Q("arch/temple", 10, -2, "Затерянный храм", "Lost Temple", "Храм археологов в джунглях юга.", "The archeologists' temple in the southern jungle.",
      [structure("betterarcheology:temple_jungle")], [xp(200)], ["arch/brush", "hot/jungle"], icon="minecraft:cracked_stone_bricks"),
    Q("arch/mesa", 10, 0, "Руины в пустошах", "Badlands Ruins", "Руины среди красных скал.", "Ruins among the red cliffs.",
      [structure("betterarcheology:mesa_ruins")], [xp(200)], ["arch/brush", "hot/badlands"], icon="minecraft:terracotta"),
    Q("arch/camp", 12, -2, "Лагерь археологов", "Archeologists' Camp", "Кто-то копал здесь до тебя.", "Someone was digging here before you.",
      [structure("betterarcheology:archeologist_camp_sand")], [xp(150)], ["arch/obelisk"], icon="minecraft:campfire"),
    Q("arch/bomb", 12, 2, "Бомба", "Bomb", "Бомбу археологов находят в раскопках. Брось её.", "Archeologists' bombs turn up in digs. Throw one.",
      [adv("betterarcheology:used_bomb_item")], [xp(100)], ["arch/identified"], icon="betterarcheology:bomb"),
    Q("arch/all_fossils", 14, 0, "Палеонтолог", "Palaeontologist", "Найди все окаменелости.", "Find every fossil.",
      [adv("betterarcheology:all_fossils_found")], [xp(800)], ["arch/identified", "arch/mesa", "arch/temple"], icon="betterarcheology:creeper_fossil", shape="gear", size=1.75),
])

# ---------------------------------------------------------------- Механика (Create beyond the first steps)
chapter("create", "create:cogwheel", "Механика", "Machinery",
        "Create: от ремня до механической руки. Всё, что крутится.",
        "Create: from a belt to a mechanical arm. Everything that turns.", [
    Q("create/goggles", 0, 0, "Очки инженера", "Engineer's Goggles", "Показывают скорость и нагрузку механизмов.", "They show the speed and stress of your machines.",
      [item("create:goggles")], [xp(40)], ["mild/alloy"], shape="hexagon", size=1.5),
    Q("create/wrench", 0, 2, "Гаечный ключ", "Wrench", "Поворачивает и разбирает детали Create.", "Turns and picks up Create's parts.",
      [item("create:wrench")], [xp(40)], ["mild/alloy"]),
    Q("create/stress", 2, 0, "Нагрузка", "Stress", "Измеритель нагрузки показывает, сколько сети ещё можно нагрузить.", "A stressometer shows how much more load your network can take.",
      [item("create:stressometer"), item("create:speedometer")], [xp(60)], ["create/goggles", "mild/wheel"]),
    Q("create/belt", 2, 2, "Конвейер", "Belt", "Ремень из сушёной ламинарии везёт предметы.", "A belt made of dried kelp carries items.",
      [item("create:belt_connector", 8)], [xp(60)], ["mild/wheel"]),
    Q("create/depot", 4, 2, "Подставка", "Depot", "Пресс и миксер работают и над подставкой.", "Presses and mixers work over a depot too.",
      [item("create:depot", 2)], [xp(40)], ["create/belt"]),
    Q("create/funnel", 6, 2, "Воронки", "Funnels", "Воронки берут и кладут предметы в сундуки и на ремень.", "Funnels take items from chests and put them on belts.",
      [item("create:andesite_funnel", 4), item("create:andesite_tunnel", 2)], [xp(60)], ["create/depot"]),
    Q("create/chute", 8, 2, "Желоб", "Chute", "Предметы падают по желобу вниз.", "Items drop down a chute.",
      [item("create:chute", 4)], [xp(40)], ["create/funnel"]),
    Q("create/vault", 10, 2, "Хранилище", "Item Vault", "Большой склад из железных листов.", "A big store made of iron sheets.",
      [item("create:item_vault", 4)], [xp(100)], ["create/chute"]),
    Q("create/fan", 2, -2, "Вентилятор", "Encased Fan", "Вентилятор через воду моет, через огонь коптит, через лаву плавит.", "Blowing through water washes, through fire smokes, through lava smelts.",
      [item("create:encased_fan")], [xp(60)], ["mild/wheel"]),
    Q("create/washing", 4, -2, "Промывка", "Washing", "Промой гравий: из него выпадают самородки.", "Wash gravel: it yields nuggets.",
      [item("minecraft:flint", 8), item("minecraft:iron_nugget", 16)], [xp(80)], ["create/fan"], icon="minecraft:gravel"),
    Q("create/millstone", 4, 0, "Жернов", "Millstone", "Мелет зерно в муку и руду в крошку.", "Grinds grain into flour and ore into grit.",
      [item("create:millstone"), item("create:wheat_flour", 8)], [xp(60)], ["create/stress"]),
    Q("create/crushing", 6, 0, "Дробильные колёса", "Crushing Wheels", "Пара колёс дробит руду с бонусом.", "A pair of wheels crushes ore with a bonus.",
      [item("create:crushing_wheel", 2)], [xp(150)], ["create/millstone"]),
    Q("create/crushed", 8, 0, "Дроблёная руда", "Crushed Ore", "Дроблёная руда плюс промывка дают больше металла.", "Crushed ore plus washing gives more metal.",
      [item("create:crushed_raw_iron", 32), item("create:crushed_raw_copper", 16)], [xp(120)], ["create/crushing", "create/washing"]),
    Q("create/windmill", 2, -4, "Ветряк", "Windmill", "Подшипник ветряка и паруса вращают без воды.", "A windmill bearing and sails turn without water.",
      [item("create:windmill_bearing"), item("create:white_sail", 8)], [xp(80)], ["mild/wheel"]),
    Q("create/pump", 4, -4, "Насос", "Pump", "Насос и трубы качают воду, лаву и всё, что течёт.", "A pump and pipes move water, lava and anything that flows.",
      [item("create:mechanical_pump"), item("create:fluid_pipe", 16)], [xp(80)], ["create/fan"]),
    Q("create/spout", 6, -4, "Разливщик", "Spout", "Разливает жидкость в бутылки и на детали.", "Pours fluids into bottles and onto parts.",
      [item("create:spout")], [xp(80)], ["create/pump", "mild/press"]),
    Q("create/rose", 6, -2, "Розовый кварц", "Rose Quartz", "Кварц Незера и редстоун. Отполируй и сделай электронную лампу.",
      "Nether quartz and redstone. Polish it and make an electron tube.",
      [item("create:polished_rose_quartz", 4), item("create:electron_tube", 4)], [xp(100)], ["create/millstone", "nether/quartz"]),
    Q("create/deployer", 8, -2, "Установщик", "Deployer", "Механическая рука, которая кликает за тебя.", "A mechanical hand that clicks for you.",
      [item("create:deployer")], [xp(120)], ["create/rose"]),
    Q("create/arm", 10, -2, "Механическая рука", "Mechanical Arm", "Перекладывает предметы между машинами.", "Moves items between machines.",
      [item("create:mechanical_arm")], [xp(200)], ["create/deployer", "hot/brass"]),
    Q("create/crafter", 10, 0, "Механический крафтер", "Mechanical Crafter", "Крафтит большие рецепты сам.", "Crafts big recipes on its own.",
      [item("create:mechanical_crafter", 9)], [xp(200)], ["create/deployer", "hot/brass"]),
    Q("create/sturdy", 8, -4, "Прочный лист", "Sturdy Sheet", "Обсидиан в порошок, порошок в лист. Нужен для поездов.", "Obsidian to powder, powder to a sheet. Trains need it.",
      [item("create:sturdy_sheet", 4)], [xp(150)], ["create/crushing", "create/spout"]),
    Q("create/diving", 10, -4, "Водолаз", "Diving Gear", "Медный баллон и шлем держат воздух под водой.", "A copper backtank and helmet hold your breath underwater.",
      [item("create:copper_backtank"), item("create:copper_diving_helmet")], [xp(150)], ["create/pump"]),
    Q("create/cannon", 12, -4, "Картофельная пушка", "Potato Cannon", "Стреляет едой. Лучше, чем кажется.", "Shoots food. Better than it sounds.",
      [item("create:potato_cannon")], [xp(100)], ["create/diving"]),
    Q("create/display", 12, 2, "Табло", "Display Board", "Табло и связь показывают, что творится на заводе.", "Display boards and links show what's going on in the factory.",
      [item("create:display_link"), item("create:display_board", 4)], [xp(100)], ["create/vault", "hot/brass"]),
])

# ---------------------------------------------------------------- Горячий остров: разведка (y 6 .. 10, x < 0)
extend("hot", [
    Q("hot/savanna", -2, 4, "Саванна", "Savanna", "Сухая саванна между пустыней и джунглями.", "Dry savanna between the desert and the jungle.",
      [biome("#c:is_savanna")], [xp(80)], ["hot/arrive"], icon="minecraft:acacia_sapling"),
    Q("hot/village", -4, 4, "Пустынная деревня", "Desert Village", "Найди деревню на юге.", "Find a village in the south.",
      [structure("minecraft:village_desert")], [xp(100)], ["hot/arrive"], icon="minecraft:cut_sandstone"),
    Q("hot/bamboo", -6, -2, "Бамбук", "Bamboo", "В джунглях растёт бамбук.", "Bamboo grows in the jungle.",
      [item("minecraft:bamboo", 32)], [xp(50)], ["hot/temple"]),
    Q("hot/cocoa", -6, -4, "Какао", "Cocoa", "Какао-бобы растут на стволах джунглей.", "Cocoa beans grow on jungle trunks.",
      [item("minecraft:cocoa_beans", 16)], [xp(60)], ["hot/jungle"]),
    Q("hot/parrot", -4, -4, "Попугай", "Parrot", "Приручи попугая семенами.", "Tame a parrot with seeds.",
      [adv("minecraft:husbandry/tame_an_animal")], [xp(60)], ["hot/jungle"], icon="minecraft:feather"),
    Q("hot/husk", -4, 6, "Кадавры", "Husks", "Зомби юга не горят на солнце.", "The south's zombies don't burn in the sun.",
      [kill("minecraft:husk", 10)], [xp(100)], ["hot/village"], icon="minecraft:rotten_flesh"),
    Q("hot/smokestack", 4, 4, "Дымоход", "Smokestack", "Бойлер Cold Sweat с дымоходом греет дом. Ночи в пустыне холодные.",
      "Cold Sweat's boiler with a smokestack heats a house. Desert nights get cold.",
      [item("cold_sweat:boiler"), item("cold_sweat:smokestack")], [xp(100)], ["hot/icebox"]),
    Q("hot/chameleon_tame", 4, 6, "Ручной хамелеон", "Pet Chameleon", "Приручи хамелеона. Он ищет биомы.", "Tame a chameleon. It finds biomes for you.",
      [adv("cold_sweat:tame_chameleon")], [xp(150)], ["hot/chameleon"], icon="cold_sweat:chameleon_molt"),
    Q("hot/insulate", 6, 6, "Утеплитель", "Insulation", "Вшей защиту от жары в броню на швейном столе.", "Sew heat protection into your armour at the sewing table.",
      [adv("cold_sweat:insulate_armor")], [xp(150)], ["hot/chameleon", "mild/sewing"], icon="cold_sweat:sewing_table"),
    Q("hot/caravan", 0, -6, "Цирк уродов", "Clown Caravan", "Фургон клоунов-зомби в саванне.", "The zombie clowns' caravan in the savanna.",
      [structure("born_in_chaos_v1:clown_caravan_savanna")], [xp(200)], ["hot/chaos1", "hot/savanna"], icon="born_in_chaos_v1:intoxicating_dagger"),
    Q("hot/firewell", 2, -6, "Огненный колодец", "Firewell", "Колодец, полный огня.", "A well full of fire.",
      [structure("born_in_chaos_v1:firewell")], [xp(200)], ["hot/chaos1"], icon="born_in_chaos_v1:fire_dust"),
    Q("hot/bic_farm", 4, -6, "Проклятая ферма", "Cursed Farm", "Здесь растут злые тыквы.", "Evil pumpkins grow here.",
      [structure("born_in_chaos_v1:farm")], [xp(200)], ["hot/chaos1"], icon="born_in_chaos_v1:evil_carved_pumpkin"),
    Q("hot/grave", 6, -6, "Могилы", "Graves", "Разрой проклятую могилу.", "Dig up a cursed grave.",
      [adv("born_in_chaos_v1:grave_ravager")], [xp(200)], ["hot/chaos1"], icon="born_in_chaos_v1:gravestone_icon"),
    Q("hot/observation", 8, -6, "Сторожевая башня", "Observation Tower", "С неё видно полостров.", "You can see half the island from it.",
      [structure("born_in_chaos_v1:observation_tower_plains")], [xp(200)], ["hot/chaos1"], icon="born_in_chaos_v1:black_argillite"),
])

# ---------------------------------------------------------------- Нефть и ток (south industry)
chapter("industry", "createdieselgenerators:diesel_engine", "Нефть и ток", "Oil and Power",
        "Нефть есть только на горячем острове: в пустынях, пустошах и саваннах. Ток идёт отсюда.",
        "Oil exists only on the hot island: in deserts, badlands and savannas. Power starts here.", [
    Q("ind/scanner", 0, 0, "Нефтеразведка", "Prospecting", "Сканер показывает, сколько нефти под чанком. Ищи в пустыне, пустошах и саванне: на других островах её нет.",
      "The scanner shows how much oil lies under a chunk. Search the desert, badlands and savanna: there is none on the other islands.",
      [item("createdieselgenerators:oil_scanner")], [xp(100)], ["hot/arrive", "mild/press"], shape="hexagon", size=1.5),
    Q("ind/pumpjack", 2, 0, "Качалка", "Pumpjack", "Подшипник, кривошип и голова качалки над скважиной.", "A bearing, a crank and a head over the well.",
      [item("createdieselgenerators:pumpjack_bearing"), item("createdieselgenerators:pumpjack_crank"), item("createdieselgenerators:pumpjack_head")], [xp(200)], ["ind/scanner"]),
    Q("ind/crude", 4, 0, "Сырая нефть", "Crude Oil", "Набери ведро сырой нефти.", "Fill a bucket with crude oil.",
      [item("createdieselgenerators:crude_oil_bucket")], [xp(150)], ["ind/pumpjack"]),
    Q("ind/barrel", 4, -2, "Бочки", "Oil Barrels", "Нефтяные бочки хранят много и ставятся на поезда и корабли.", "Oil barrels hold plenty and ride on trains and ships.",
      [item("createdieselgenerators:oil_barrel", 4)], [xp(80)], ["ind/crude"]),
    Q("ind/distill", 6, 0, "Перегонка", "Distillation", "Контроллер на колонне из резервуаров разгоняет нефть на фракции.", "A controller on a column of fluid tanks splits oil into fractions.",
      [item("createdieselgenerators:distillation_controller"), item("create:fluid_tank", 4)], [xp(250)], ["ind/crude", "hot/burner"]),
    Q("ind/diesel", 8, 0, "Дизель", "Diesel", "Набери ведро дизеля.", "Fill a bucket with diesel.",
      [item("createdieselgenerators:diesel_bucket")], [xp(150)], ["ind/distill"]),
    Q("ind/gasoline", 8, -2, "Бензин", "Gasoline", "Ещё одна фракция.", "Another fraction.",
      [item("createdieselgenerators:gasoline_bucket")], [xp(150)], ["ind/distill"]),
    Q("ind/engine", 10, 0, "Дизельный двигатель", "Diesel Engine", "Дизель даёт вращение без воды и ветра.", "Diesel gives rotation without water or wind.",
      [item("createdieselgenerators:diesel_engine")], [xp(200)], ["ind/diesel"]),
    Q("ind/large", 12, 0, "Большой двигатель", "Large Diesel Engine", "Мощнее, если собрать из нескольких.", "Stronger when built from several.",
      [item("createdieselgenerators:large_diesel_engine", 2)], [xp(300)], ["ind/engine"]),
    Q("ind/huge", 14, 0, "Огромный двигатель", "Huge Diesel Engine", "Самый мощный двигатель Diesel Generators.", "Diesel Generators' strongest engine.",
      [item("createdieselgenerators:huge_diesel_engine")], [xp(500)], ["ind/large"], shape="hexagon", size=1.25),
    Q("ind/asphalt", 10, -2, "Асфальт", "Asphalt", "Остатки перегонки идут на асфальт. По нему быстрее бегать.", "What's left from distillation becomes asphalt. You run faster on it.",
      [item("createdieselgenerators:asphalt_block", 32)], [xp(100)], ["ind/distill"]),
    Q("ind/plant_oil", 2, -2, "Масло из семян", "Plant Oil", "Ферментер делает биотопливо из растений. Это запасной путь без нефти.",
      "The fermenter makes fuel from plants. It's the backup plan without oil.",
      [item("createdieselgenerators:bulk_fermenter")], [xp(150)], ["ind/scanner"]),
    Q("ind/oritech_oil", 2, 2, "Нефтяной источник", "Oil Spring", "Oritech находит свою нефть в источниках: они тоже только на юге.", "Oritech has its own oil in springs: those are only in the south too.",
      [item("oritech:fuel_generator_block")], [xp(200)], ["ind/scanner", "hot/generator"]),
    # Create New Age
    Q("ind/magnetite", 0, 4, "Магнетит", "Magnetite", "Магнетит нужен для магнитов New Age.", "New Age's magnets need magnetite.",
      [item("create_new_age:magnetite_block", 8)], [xp(80)], ["hot/arrive"]),
    Q("ind/energiser", 2, 4, "Энергайзер", "Energiser", "Заряжает железо и золото током.", "Charges iron and gold with power.",
      [item("create_new_age:basic_energiser")], [xp(150)], ["ind/magnetite", "hot/brass"]),
    Q("ind/overcharged", 4, 4, "Заряженное железо", "Overcharged Iron", "Железо, заряженное в энергайзере.", "Iron charged in the energiser.",
      [item("create_new_age:overcharged_iron", 8)], [xp(150)], ["ind/energiser"]),
    Q("ind/wire", 4, 6, "Провода", "Wires", "Медный провод и соединители.", "Copper wire and connectors.",
      [item("create_new_age:copper_wire", 32), item("create_new_age:electrical_connector", 4)], [xp(100)], ["ind/energiser"]),
    Q("ind/generator", 6, 4, "Генератор New Age", "New Age Generator", "Катушки и щётки превращают вращение в ток.", "Coils and brushes turn rotation into power.",
      [item("create_new_age:generator_coil", 4), item("create_new_age:carbon_brushes")], [xp(200)], ["ind/overcharged"]),
    Q("ind/motor", 8, 4, "Мотор New Age", "New Age Motor", "И обратно: ток во вращение.", "And back: power into rotation.",
      [item("create_new_age:basic_motor")], [xp(150)], ["ind/generator"]),
    Q("ind/advanced_motor", 10, 4, "Усиленный мотор", "Advanced Motor", "Быстрее и сильнее.", "Faster and stronger.",
      [item("create_new_age:advanced_motor")], [xp(250)], ["ind/motor"]),
    Q("ind/solar", 6, 6, "Солнце юга", "Southern Sun", "Солнечная нагревательная пластина. На юге солнца хватает.", "A solar heating plate. There's plenty of sun in the south.",
      [item("create_new_age:basic_solar_heating_plate", 4)], [xp(150)], ["ind/overcharged"]),
    Q("ind/stirling", 8, 6, "Двигатель Стирлинга", "Stirling Engine", "Тепло во вращение.", "Heat into rotation.",
      [item("create_new_age:stirling_engine")], [xp(150)], ["ind/solar"]),
    Q("ind/overcharged_gold", 6, 8, "Заряженное золото", "Overcharged Gold", "Для проводов и магнитов получше.", "For better wires and magnets.",
      [item("create_new_age:overcharged_gold", 8)], [xp(200)], ["ind/overcharged"]),
    Q("ind/diamond", 8, 8, "Заряженный алмаз", "Overcharged Diamond", "Заряженный алмаз и провод из него.", "An overcharged diamond and wire made from it.",
      [item("create_new_age:overcharged_diamond", 2), item("create_new_age:overcharged_diamond_wire", 4)], [xp(300)], ["ind/overcharged_gold"]),
    # Oritech, next steps
    Q("ind/item_pipe", 0, -4, "Трубы Oritech", "Oritech Pipes", "Трубы для предметов и энергии.", "Pipes for items and energy.",
      [item("oritech:item_pipe", 16), item("oritech:energy_pipe", 16)], [xp(100)], ["hot/generator"]),
    Q("ind/battery", 2, -4, "Батарея", "Battery", "Запас энергии Oritech.", "Oritech's energy storage.",
      [item("oritech:basic_battery")], [xp(100)], ["ind/item_pipe"]),
    Q("ind/assembler", 4, -4, "Сборщик", "Assembler", "Собирает сложные детали Oritech.", "Builds Oritech's complex parts.",
      [item("oritech:assembler_block")], [xp(200)], ["hot/steel"]),
    Q("ind/centrifuge", 6, -4, "Центрифуга", "Centrifuge", "Разделяет вещества.", "Separates materials.",
      [item("oritech:centrifuge_block")], [xp(200)], ["ind/assembler"]),
    Q("ind/solar_oritech", 8, -4, "Солнечная панель", "Solar Panel", "Большая солнечная панель Oritech.", "Oritech's big solar panel.",
      [item("oritech:big_solar_panel_block")], [xp(250)], ["ind/assembler"]),
    Q("ind/power", 16, 0, "Электрификация юга", "Electrifying the South", "Нефть, дизель, ток New Age и Oritech: у тебя своя энергосеть.",
      "Oil, diesel, New Age and Oritech power: you run your own grid.",
      [check()], [xp(1000)], ["ind/huge", "ind/advanced_motor", "ind/diamond", "ind/solar_oritech"], icon="minecraft:redstone_block", shape="gear", size=2.0),
])

# ---------------------------------------------------------------- Край
extend("end", [
    Q("end/slot", 8, 0, "Слот для крыльев", "Wing Slot", "Элитры надеваются в отдельный слот (Elytra Slot), так что нагрудник остаётся на месте. На север лети в утеплённом нагруднике.",
      "Elytra go into their own slot (Elytra Slot), so your chestplate stays on. Fly north in a fur-lined chestplate.",
      [check()], [xp(50)], ["end/elytra"], icon="minecraft:elytra"),
    Q("end/powder", 2, -2, "Порошок эндерняка", "End Stone Powder", "Дробильные колёса превращают эндерняк в порошок. Из него делают левитит для кораблей.",
      "Crushing wheels turn end stone into powder. Levitite for airships is made from it.",
      [item("aeronautics:end_stone_powder", 32)], [xp(150)], ["end/enter", "create/crushing"]),
    Q("end/chorus", 4, -2, "Хорус", "Chorus", "Плоды хоруса с внешних островов.", "Chorus fruit from the outer islands.",
      [item("minecraft:chorus_fruit", 16)], [xp(100)], ["end/dragon"]),
    Q("end/shulker_box", 6, 4, "Шалкеровый ящик", "Shulker Box", "Ящик, который носят с собой. Перелёт на север станет проще.", "A box you carry around. The flight north gets easier.",
      [item("minecraft:shulker_box")], [xp(150)], ["end/shulker"]),
    Q("end/platinum", 4, 4, "Платина Края", "End Platinum", "В эндерняке Края есть платиновая руда Oritech. Это единственное место кроме севера.",
      "End stone holds Oritech's platinum ore. It's the only place besides the north.",
      [item("oritech:raw_platinum", 4)], [xp(200)], ["end/dragon"]),
    Q("end/egg", 0, 2, "Яйцо дракона", "Dragon Egg", "Забери яйцо с портала.", "Take the egg from the portal.",
      [item("minecraft:dragon_egg")], [xp(300)], ["end/dragon"]),
])

# ---------------------------------------------------------------- Небо (Create Aeronautics)
chapter("sky", "aeronautics:white_envelope", "Небо", "The Sky",
        "Корабли Create Aeronautics. Над Бледнотой воздух редеет: к высоте 300 ни шар, ни винт уже не тянут.",
        "Create Aeronautics airships. Above the Pale the air thins: by height 300 no balloon or propeller can climb.", [
    Q("sky/envelope", 0, 0, "Оболочка", "Envelope", "Оболочку шара шьют из шерсти и палок.", "A balloon's envelope is sewn from wool and sticks.",
      [item("aeronautics:white_envelope", 32)], [xp(80)], ["mild/wool", "mild/alloy"], shape="hexagon", size=1.5),
    Q("sky/burner", 2, 0, "Горелка", "Hot Air Burner", "Горелка греет воздух в оболочке.", "The burner heats the air in the envelope.",
      [item("aeronautics:adjustable_burner")], [xp(100)], ["sky/envelope", "mild/press"]),
    Q("sky/assembler", 2, 2, "Физический сборщик", "Physics Assembler", "Превращает постройку в летающее тело.", "Turns a build into a flying body.",
      [item("simulated:physics_assembler")], [xp(100)], ["sky/envelope"]),
    Q("sky/balloon", 4, 0, "Голова в облаках", "Head in the Clouds", "Наполни оболочку горячим воздухом.", "Fill an airtight envelope with hot air.",
      [adv("aeronautics:head_in_the_clouds")], [xp(200)], ["sky/burner", "sky/assembler"], icon="aeronautics:red_envelope"),
    Q("sky/steering", 4, 2, "Штурвал", "Steering", "Штурвал и рычаг тяги.", "A steering wheel and a throttle lever.",
      [item("simulated:steering_wheel"), item("simulated:throttle_lever")], [xp(100)], ["sky/assembler"]),
    Q("sky/sail", 2, 4, "Паруса", "Sails", "Симметричные паруса ловят ветер.", "Symmetric sails catch the wind.",
      [item("simulated:white_symmetric_sail", 8)], [xp(80)], ["sky/assembler"]),
    Q("sky/propeller", 6, 0, "Пропеллер", "Propeller", "Подшипник пропеллера на латунном корпусе.", "A propeller bearing on a brass casing.",
      [item("aeronautics:propeller_bearing"), item("aeronautics:wooden_propeller", 2)], [xp(200)], ["sky/balloon", "hot/brass"]),
    Q("sky/thrust", 8, 0, "Тяга", "Thrust", "Запусти пропеллер на корабле.", "Run a propeller on a ship.",
      [adv("aeronautics:in_thrust_we_trust")], [xp(250)], ["sky/propeller"], icon="aeronautics:andesite_propeller"),
    Q("sky/altitude", 6, 2, "Датчик высоты", "Altitude Sensor", "Показывает высоту и давление. Давление падает с высотой, и тяга вместе с ним.",
      "It shows height and air pressure. Pressure drops with height, and so does thrust.",
      [item("simulated:altitude_sensor")], [xp(150)], ["sky/balloon"]),
    Q("sky/ceiling", 8, 2, "Потолок Бледноты", "The Pale's Ceiling",
      "Поднимись на корабле как можно выше. Около высоты 300 воздуха уже нет, и корабль дальше не идёт. А выше 450, где нет яда, стоять на палубе нельзя: Бледнота отпускает только тех, кто летит на крыльях.",
      "Take a ship as high as it goes. Around height 300 there's no air left, and the ship stops. Above 450, where the poison ends, nobody can stand on a deck: the Pale only lets go of those on wings.",
      [check()], [xp(150)], ["sky/altitude"], icon="minecraft:gray_dye"),
    Q("sky/goggles", 4, 4, "Очки пилота", "Aviator's Goggles", "Стильно.", "Stylish.",
      [item("aeronautics:aviators_goggles")], [xp(60)], ["sky/steering"]),
    Q("sky/navigation", 6, 4, "Штурманский стол", "Navigation Table", "Ведёт корабль по курсу.", "Keeps a ship on course.",
      [item("simulated:navigation_table")], [xp(150)], ["sky/steering", "hot/brass"]),
    Q("sky/winch", 8, 4, "Лебёдка", "Rope Winch", "Поднимает груз на канате.", "Lifts cargo on a rope.",
      [item("simulated:rope_winch")], [xp(100)], ["sky/steering"]),
    Q("sky/docking", 10, 4, "Стыковка", "Docking", "Стыковочный разъём сцепляет два корабля.", "A docking connector couples two ships.",
      [item("simulated:docking_connector")], [xp(200)], ["sky/winch", "create/crafter"]),
    Q("sky/blend", 10, -2, "Левититовая смесь", "Levitite Blend", "Порошок эндерняка, цинк и вода в нагретом миксере.", "End stone powder, zinc and water in a heated mixer.",
      [item("aeronautics:levitite_blend_bucket")], [xp(250)], ["end/powder", "hot/burner"]),
    Q("sky/levitite", 12, -2, "Левитит", "Levitite", "Нагрей смесь, и она кристаллизуется. Левитит держит корабль в воздухе, но поднять его сам не может.",
      "Heat the blend and it crystallises. Levitite holds a ship up, but can't lift it on its own.",
      [adv("aeronautics:unidentified_floating_object")], [xp(400)], ["sky/blend"], icon="aeronautics:levitite"),
    Q("sky/pearl", 14, -2, "Перламутровый левитит", "Pearlescent Levitite", "Есть способ сделать его переливчатым. Найди его сам.", "There's a way to make it shimmer. Find it yourself.",
      [adv("aeronautics:now_available_in_pink")], [xp(300)], ["sky/levitite"], icon="aeronautics:pearlescent_levitite"),
    Q("sky/gyro", 10, 0, "Гироскопический винт", "Gyroscopic Propeller", "Держит корабль ровно.", "Keeps a ship level.",
      [item("aeronautics:gyroscopic_propeller_bearing")], [xp(300)], ["sky/thrust", "create/crafter"]),
    Q("sky/smart", 12, 0, "Умный пропеллер", "Smart Propeller", "Маленький винт для точного манёвра.", "A small propeller for fine moves.",
      [item("aeronautics:smart_propeller", 2)], [xp(200)], ["sky/gyro"]),
    Q("sky/cannon", 12, 2, "Бортовая пушка", "Mounted Cannon", "Картофельная пушка на борту.", "A potato cannon on deck.",
      [item("aeronautics:mounted_potato_cannon")], [xp(200)], ["sky/thrust", "create/cannon"]),
    Q("sky/wheels", 0, -2, "Вездеход", "Rover", "Offroad добавляет колёса. Корабль может ездить и по земле.", "Offroad adds wheels. A ship can drive on land as well.",
      [item("offroad:wheel_mount", 4), item("offroad:tire", 4)], [xp(150)], ["sky/assembler"], icon="offroad:tire"),
    Q("sky/borehead", 2, -2, "Буровой подшипник", "Borehead", "Бур для подземной машины.", "A drill for an underground machine.",
      [item("offroad:borehead_bearing")], [xp(200)], ["sky/wheels"]),
    Q("sky/cargo", 14, 0, "Грузовой рейс", "Cargo Run",
      "Переправь груз с одного острова на другой кораблём или поездом без людей на борту: человека туман убьёт, а сундуки нет. Отметь квест, когда груз дошёл.",
      "Move cargo from one island to another by ship or train with nobody aboard: the fog kills people, not chests. Tick the quest off when the cargo arrives.",
      [check()], [xp(1000)], ["sky/smart", "sky/levitite", "sky/navigation"], icon="minecraft:chest_minecart", shape="gear", size=1.75),
])

# ---------------------------------------------------------------- Холодный остров (y 6 .. 10 and x < 0)
extend("cold", [
    Q("cold/meal", 4, 4, "Горячий ужин", "Hot Dinner", "Съешь горячий суп или рагу на севере: тёплая еда греет тело на минуту.",
      "Eat a hot soup or stew in the north: warm food warms your body for a minute.",
      [adv("farmersdelight:main/eat_nourishing_food")], [xp(150)], ["cold/arrive", "kitchen/soup"], icon="farmersdelight:beef_stew"),
    Q("cold/full_fur", 6, 4, "Полная шуба", "Full Fur", "Полный комплект брони из козьего меха.", "A full set of goat fur armour.",
      [item("cold_sweat:goat_fur_helmet"), item("cold_sweat:goat_fur_chestplate"), item("cold_sweat:goat_fur_leggings"), item("cold_sweat:goat_fur_boots")],
      [xp(250)], ["cold/fur"], icon="cold_sweat:goat_fur_helmet"),
    Q("cold/insulation", 8, 4, "Полное утепление", "Fully Insulated", "Утепли весь комплект брони на швейном столе.", "Insulate a full set of armour at the sewing table.",
      [adv("cold_sweat:full_insulation")], [xp(300)], ["cold/full_fur", "mild/sewing"], icon="cold_sweat:sewing_table"),
    Q("cold/huddle", 10, 4, "Погреться вместе", "Huddle Close", "Прижмись к другу или к питомцу, чтобы согреться.", "Huddle up to a friend or a pet to keep warm.",
      [adv("cold_sweat:huddle_close")], [xp(100)], ["cold/arrive"], icon="minecraft:white_wool"),
    Q("cold/igloo", 0, -2, "Иглу", "Igloo", "Найди иглу.", "Find an igloo.",
      [structure("minecraft:igloo")], [xp(150)], ["cold/arrive"], icon="minecraft:snow_block"),
    Q("cold/frost_dungeon", 0, -4, "Ледяное подземелье", "Frosted Dungeon", "Подземелье Terralith во льду.", "Terralith's dungeon in the ice.",
      [structure("terralith:underground/frosted_dungeon")], [xp(250)], ["cold/igloo"], icon="minecraft:blue_ice"),
    Q("cold/glacial", 2, -6, "Ледниковая хижина", "Glacial Hut", "Хижина Terralith среди ледников.", "A Terralith hut among the glaciers.",
      [structure("terralith:glacial_hut")], [xp(200)], ["cold/spikes"], icon="minecraft:packed_ice"),
    Q("cold/strays", 0, 2, "Зимогоры", "Strays", "Скелеты севера стреляют замедляющими стрелами.", "The north's skeletons shoot slowing arrows.",
      [kill("minecraft:stray", 10)], [xp(150)], ["cold/arrive"], icon="minecraft:tipped_arrow"),
    Q("cold/permafrost", 16, 4, "Вечная мерзлота", "Permafrost", "Осколки вечной мерзлоты. Из них куют Морозный клинок.", "Permafrost shards. The Frostbitten Blade is forged from them.",
      [item("born_in_chaos_v1:permafrost_shard", 8), item("born_in_chaos_v1:frostbitten_blade")], [xp(300)], ["cold/krampus"], icon="born_in_chaos_v1:frostbitten_blade"),
    Q("cold/horn", 16, 6, "Рог бури", "Stormcaller's Horn", "Рог Крампуса и мерзлота дают рог снежной бури.", "Krampus' horn and permafrost make a horn of snowstorms.",
      [item("born_in_chaos_v1:stormcallers_horn")], [xp(300)], ["cold/permafrost"]),
    Q("cold/soulbane", 18, 2, "Губитель душ", "Soulbane", "Меч Лорда Тыквоголового.", "Lord Pumpkinhead's sword.",
      [item("born_in_chaos_v1:soulbane")], [xp(500)], ["cold/pumpkinhead"]),
    Q("cold/charms", 12, 4, "Амулеты", "Charms", "Миссионер роняет амулеты. Собери три разных.", "The Missioner drops charms. Collect three different ones.",
      [item("born_in_chaos_v1:charmof_power"), item("born_in_chaos_v1:charmof_resistance"), item("born_in_chaos_v1:charmof_endurance")], [xp(400)], ["cold/missioner"], icon="born_in_chaos_v1:charmof_power"),
    # New Age: the nuclear stage lives here too
    Q("cold/thorium", 6, -6, "Торий", "Thorium", "Ториевая руда Create New Age есть только на холодном острове, рядом с ураном Oritech.",
      "Create New Age's thorium ore exists only on the cold island, next to Oritech's uranium.",
      [item("create_new_age:thorium", 8)], [xp(300)], ["cold/ores"], icon="create_new_age:thorium_ore"),
    Q("cold/casing", 8, -6, "Корпус реактора", "Reactor Casing", "Корпус реактора собирают на линии сборки.", "The reactor casing is made on an assembly line.",
      [item("create_new_age:reactor_casing", 8), item("create_new_age:reactor_glass", 4)], [xp(400)], ["cold/thorium", "ind/overcharged"]),
    Q("cold/rods", 10, -6, "Стержни", "Rods", "Регулирующие стержни и приёмник топлива.", "Control rods and a fuel acceptor.",
      [item("create_new_age:reactor_rod", 4), item("create_new_age:reactor_fuel_acceptor")], [xp(400)], ["cold/casing"]),
    Q("cold/fuel", 10, -8, "Ядерное топливо", "Nuclear Fuel", "Топливо из тория.", "Fuel made of thorium.",
      [item("create_new_age:nuclear_fuel", 4)], [xp(500)], ["cold/rods"]),
    Q("cold/reactor", 12, -6, "Реактор", "Reactor", "Собери реактор с отводом тепла. Держи его подальше от дома.", "Build a reactor with heat vents. Keep it far from home.",
      [item("create_new_age:reactor_heat_vent", 4)], [xp(800)], ["cold/fuel"], icon="create_new_age:reactor_rod", shape="hexagon", size=1.5),
    Q("cold/exo", 10, -2, "Экзоброня", "Exo Armour", "Экзоброня Oritech из северных металлов.", "Oritech's exo armour from northern metals.",
      [item("oritech:exo_chestplate")], [xp(500)], ["cold/jetpack"]),
])

# ---------------------------------------------------------------- Бестиарий хаоса (Born in Chaos)
chapter("chaos", "born_in_chaos_v1:dark_metal_ingot", "Бестиарий хаоса", "Bestiary of Chaos",
        "Порождения хаоса живут на горячем и холодном островах. На стартовом их нет.",
        "The spawn of chaos lives on the hot and cold islands. There is none on the starter island.", [
    Q("chaos/combat", 0, 0, "Новый бой", "New Combat",
      "Better Combat меняет бой: оружие бьёт сериями, у каждого своя дальность. Тёмный боевой клинок стал двуручным мечом, Секира жнеца двуручным топором, Молот-черепокол двуручным молотом, а вилы копьём. Посохи хаоса бьют по ПКМ, как раньше.",
      "Better Combat changes how fights feel: weapons hit in combos, each with its own reach. The Dark Warblade is a two-handed claymore now, the Great Reaper Axe a two-handed axe, the Skullbreaker a two-handed hammer and the pitchfork a spear. Chaos staffs still work on right-click.",
      [check()], [xp(30)], ["hot/chaos1"], icon="born_in_chaos_v1:darkwarblade", shape="hexagon", size=1.5),
    Q("chaos/decaying", 2, -4, "Гниющий зомби", "Decaying Zombie", "", "",
      [kill("born_in_chaos_v1:decaying_zombie", 5)], [xp(80)], ["chaos/combat"], icon="born_in_chaos_v1:corpse_maggot"),
    Q("chaos/barrel", 4, -4, "Зомби в бочке", "Barrel Zombie", "", "",
      [kill("born_in_chaos_v1:barrel_zombie", 3)], [xp(100)], ["chaos/decaying"], icon="minecraft:barrel"),
    Q("chaos/clown", 6, -4, "Зомби-клоун", "Zombie Clown", "От его ножа кружится голова.", "His knife makes your head spin.",
      [kill("born_in_chaos_v1:zombie_clown", 3)], [xp(150)], ["chaos/barrel"], icon="born_in_chaos_v1:intoxicating_dagger"),
    Q("chaos/lumberjack", 8, -4, "Зомби-лесоруб", "Zombie Lumberjack", "Его топор колет и щиты.", "His axe splits shields too.",
      [kill("born_in_chaos_v1:zombie_lumberjack"), item("born_in_chaos_v1:wood_splitter_axe")], [xp(150)], ["chaos/clown"], icon="born_in_chaos_v1:wood_splitter_axe"),
    Q("chaos/fisherman", 10, -4, "Зомби-рыбак", "Zombie Fisherman", "Живёт у воды.", "Lives by the water.",
      [kill("born_in_chaos_v1:zombie_fisherman", 2)], [xp(120)], ["chaos/clown"], icon="minecraft:fishing_rod"),
    Q("chaos/bruiser", 12, -4, "Зомби-громила", "Zombie Bruiser", "Бьёт больно.", "Hits hard.",
      [kill("born_in_chaos_v1:zombie_bruiser", 2)], [xp(150)], ["chaos/lumberjack"], icon="born_in_chaos_v1:monster_flesh"),
    Q("chaos/thrasher", 2, -2, "Скелет-громила", "Skeleton Thrasher", "Его молот оглушает.", "His hammer stuns.",
      [kill("born_in_chaos_v1:skeleton_thrasher", 3)], [xp(150)], ["chaos/combat"], icon="born_in_chaos_v1:skullbreaker_hammer"),
    Q("chaos/demoman", 4, -2, "Скелет-подрывник", "Skeleton Demoman", "Держись подальше, он бросает бомбы.", "Keep your distance: he throws bombs.",
      [kill("born_in_chaos_v1:skeleton_demoman", 2)], [xp(150)], ["chaos/thrasher"], icon="born_in_chaos_v1:damned_demomans_hat_helmet"),
    Q("chaos/decrepit", 6, -2, "Дряхлый скелет", "Decrepit Skeleton", "", "",
      [kill("born_in_chaos_v1:decrepit_skeleton", 5)], [xp(100)], ["chaos/thrasher"], icon="minecraft:bone"),
    Q("chaos/siamese", 8, -2, "Сиамские скелеты", "Siamese Skeletons", "Два черепа, один хребет.", "Two skulls, one spine.",
      [kill("born_in_chaos_v1:siamese_skeletons")], [xp(200)], ["chaos/decrepit"], icon="born_in_chaos_v1:fused_bone"),
    Q("chaos/imp", 10, -2, "Костяной бес", "Bone Imp", "", "",
      [kill("born_in_chaos_v1:bone_imp", 3)], [xp(120)], ["chaos/decrepit"], icon="born_in_chaos_v1:bundle_of_bones"),
    Q("chaos/supreme", 12, -2, "Верховный призыватель", "Supreme Bonescaller", "Старший из призывателей костей.", "The eldest of the bonescallers.",
      [kill("born_in_chaos_v1:supreme_bonescaller")], [xp(400)], ["chaos/siamese"], icon="born_in_chaos_v1:orbofthe_summoner"),
    Q("chaos/hounds", 2, 2, "Ужасные гончие", "Dread Hounds", "Охотятся стаей.", "They hunt in packs.",
      [kill("born_in_chaos_v1:dread_hound", 5)], [xp(150)], ["chaos/combat"], icon="born_in_chaos_v1:hound_trap"),
    Q("chaos/mound", 4, 2, "Курган гончих", "Mound of Hounds", "Логово гончих.", "The hounds' lair.",
      [structure("born_in_chaos_v1:mound_of_hounds")], [xp(200)], ["chaos/hounds"], icon="born_in_chaos_v1:closed_hound_trap"),
    Q("chaos/phantom_creeper", 6, 2, "Призрачный крипер", "Phantom Creeper", "Взрывается дважды.", "Explodes twice.",
      [kill("born_in_chaos_v1:phantom_creeper", 2)], [xp(150)], ["chaos/combat"], icon="born_in_chaos_v1:phantom_powder"),
    Q("chaos/spirits", 8, 2, "Неупокоенные духи", "Restless Spirits", "Духов лучше бить Духорезом.", "Spirits take the most from a Spirit Divider.",
      [kill("born_in_chaos_v1:restless_spirit", 3), kill("born_in_chaos_v1:seared_spirit", 2)], [xp(200)], ["chaos/combat"], icon="born_in_chaos_v1:ethereal_spirit"),
    Q("chaos/firelight", 10, 2, "Огонёк", "Firelight", "", "",
      [kill("born_in_chaos_v1:firelight", 3)], [xp(120)], ["chaos/spirits"], icon="born_in_chaos_v1:fire_dust"),
    Q("chaos/flies", 2, 4, "Мухи и оводы", "Flies and Gadflies", "Мелкие, но надоедливые.", "Small but annoying.",
      [kill("born_in_chaos_v1:corpse_fly", 5), kill("born_in_chaos_v1:bloody_gadfly", 3)], [xp(120)], ["chaos/combat"], icon="born_in_chaos_v1:bloody_gadfly_eye"),
    Q("chaos/crab", 4, 4, "Шипастый краб", "Thornshell Crab", "Его панцирь идёт на булаву и броню.", "Its shell makes a mace and armour.",
      [kill("born_in_chaos_v1:thornshell_crab", 3)], [xp(150)], ["chaos/combat"], icon="born_in_chaos_v1:shell_mace"),
    Q("chaos/fish", 6, 4, "Рыбы-трупоеды", "Carrion Fish", "Гадость из воды.", "Nasty things in the water.",
      [kill("born_in_chaos_v1:corpse_fish", 3), kill("born_in_chaos_v1:glutton_fish")], [xp(150)], ["chaos/combat"], icon="born_in_chaos_v1:rotten_fish"),
    Q("chaos/termite", 8, 4, "Алмазный термит", "Diamond Termite", "Он ест алмазную руду.", "It eats diamond ore.",
      [kill("born_in_chaos_v1:diamond_termite", 2)], [xp(200)], ["chaos/combat"], icon="born_in_chaos_v1:diamond_termite_shard"),
    Q("chaos/pumpkins", 10, 4, "Тыквенная братия", "The Pumpkin Gang", "Тыквоголовый и его громилы.", "Pumpkinhead and his bruisers.",
      [kill("born_in_chaos_v1:pumpkinhead"), kill("born_in_chaos_v1:pumpkin_bruiser", 2)], [xp(250)], ["chaos/combat"], icon="born_in_chaos_v1:evil_carved_pumpkin"),
    Q("chaos/sir", 12, 4, "Сэр Тыквоголовый", "Sir Pumpkinhead", "Всадник без головы, младший брат Лорда.", "The headless rider, the Lord's younger brother.",
      [kill("born_in_chaos_v1:sir_pumpkinhead")], [xp(400)], ["chaos/pumpkins"], icon="born_in_chaos_v1:pumpkinstaffa"),
    Q("chaos/persecutor", 12, 2, "Алый преследователь", "Scarlet Persecutor", "", "",
      [kill("born_in_chaos_v1:scarlet_persecutor")], [xp(300)], ["chaos/spirits"], icon="born_in_chaos_v1:scarlet_stained_glass"),
    # Gear
    Q("chaos/dagger", 0, 6, "Ритуальный кинжал", "Ritual Dagger", "Жертва лечит и даёт Силу.", "A sacrifice heals you and gives Strength.",
      [item("born_in_chaos_v1:dark_ritual_dagger")], [xp(150)], ["hot/darkmetal"]),
    Q("chaos/reaper", 2, 6, "Секира жнеца", "Great Reaper Axe", "Каждое убийство разгоняет Rampage.", "Every kill stokes your Rampage.",
      [item("born_in_chaos_v1:great_reaper_axe")], [xp(250)], ["hot/darkmetal"]),
    Q("chaos/skullbreaker", 4, 6, "Молот-черепокол", "Skullbreaker Hammer", "Удар в прыжке оглушает.", "A jumping blow stuns.",
      [item("born_in_chaos_v1:skullbreaker_hammer")], [xp(250)], ["hot/darkmetal"]),
    Q("chaos/scythe", 6, 6, "Коса кошмара", "Nightmare Scythe", "Вампиризм и иссушение.", "Lifesteal and wither.",
      [item("born_in_chaos_v1:nightmare_scythe")], [xp(300)], ["hot/darkmetal"]),
    Q("chaos/shell_armor", 8, 6, "Шипастый панцирь", "Spiny Shell Armour", "Броня из панцирей краба.", "Armour from crab shells.",
      [item("born_in_chaos_v1:spiny_shell_armor_chestplate")], [xp(200)], ["chaos/crab"]),
    Q("chaos/mantle", 10, 6, "Мантия ночи", "Mantle of the Night", "Нагрудник из кожи монстров и тёмного металла.", "A chestplate of monster skin and dark metal.",
      [item("born_in_chaos_v1:nightmare_mantleofthe_night_chestplate")], [xp(300)], ["hot/darkmetal"]),
    Q("chaos/full_dark", 12, 6, "Рыцарь хаоса", "Chaos Knight", "Полный комплект тёмного металла. В этой сборке он даёт Силу I.",
      "A full set of dark metal. In this pack it gives Strength I.",
      [item("born_in_chaos_v1:dark_metal_armor_helmet"), item("born_in_chaos_v1:dark_metal_armor_chestplate"),
       item("born_in_chaos_v1:dark_metal_armor_leggings"), item("born_in_chaos_v1:dark_metal_armor_boots")],
      [xp(600)], ["hot/darkmetal"], icon="born_in_chaos_v1:dark_metal_armor_helmet", shape="hexagon", size=1.5),
    Q("chaos/rampage", 14, 6, "Ярость", "Rampage", "Свари зелье ярости.", "Brew a potion of rampage.",
      [item("born_in_chaos_v1:potion_of_rampage")], [xp(200)], ["chaos/reaper"]),
    Q("chaos/hunter", 14, 0, "Охотник на хаос", "Chaos Hunter", "Ты знаешь всех, кто приходит из хаоса.", "You know everything that crawls out of chaos.",
      [check()], [xp(1500)], ["chaos/bruiser", "chaos/supreme", "chaos/sir", "chaos/persecutor", "chaos/termite", "chaos/mound"],
      icon="born_in_chaos_v1:chaos_icon", shape="gear", size=2.0),
])

# ---------------------------------------------------------------- Зачарования: древние книги Better Archeology
extend("enchant", [
    Q("enchant/soaring", 10, -1, "Парящий ветер", "Soaring Winds", "Древнее зачарование для элитр. Заслужи его перелётом на север.",
      "An ancient enchantment for elytra. Earn it by flying north.",
      [check()], [book("betterarcheology:soaring_winds")], ["cold/arrive"], icon="minecraft:elytra"),
    Q("enchant/penetrating", 10, 0, "Пробивающий удар", "Penetrating Strike", "Удар проходит сквозь броню. Заслужи книгу, победив разорителя из набега.", "The blow goes through armour. Earn the book by beating a ravager from a raid.",
      [kill("minecraft:ravager")], [book("betterarcheology:penetrating_strike")], ["mild/raid", "enchant/anvil"], icon="minecraft:iron_sword"),
    Q("enchant/tunneling", 10, 1, "Проходка", "Tunneling", "Кирка копает сразу несколько блоков. Заслужи книгу, накопав глубинного сланца.", "A pickaxe digs several blocks at once. Earn the book by digging deepslate.",
      [item("minecraft:cobbled_deepslate", 256)], [book("betterarcheology:tunneling")], ["enchant/anvil"], icon="minecraft:diamond_pickaxe"),
])


# Chapter order in the book: each island's chapter, then the side chapters that grow out of it.
ORDER = ["mild", "kitchen", "archeology", "create", "nether", "hot", "industry", "end", "sky", "cold", "chaos", "enchant"]
CHAPTERS.sort(key=lambda c: ORDER.index(c["key"]))
CHAPTERS[:] = [c for c in CHAPTERS if c["key"] in BUILD]


# ---------------------------------------------------------------- SNBT writer
def fix_numbers(o, parent=None):
    """Turn Python ints into the right NBT kind: item counts are ints, task counts/values are longs."""
    if isinstance(o, dict):
        out = {}
        for k, v in o.items():
            if isinstance(v, (Int, Long)):
                out[k] = v
            elif isinstance(v, bool) or not isinstance(v, int):
                out[k] = fix_numbers(v, k)
            elif k in ("count", "value") and parent == "task":
                out[k] = Long(v)
            else:
                out[k] = Int(v)
        return out
    if isinstance(o, IntArray):
        return o
    if isinstance(o, list):
        return [fix_numbers(x, parent) for x in o]
    return o




def snbt2(v, ind=0):
    t = "\t"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, Long):
        return f"{int(v)}L"
    if isinstance(v, int):
        return str(int(v))
    if isinstance(v, float):
        return f"{v}d"
    if isinstance(v, str):
        return '"' + v.replace("\\", "\\\\").replace('"', '\\"') + '"'
    if isinstance(v, IntArray):
        return "[I; " + ", ".join(str(int(x)) for x in v) + "]"
    if isinstance(v, list):
        if not v:
            return "[ ]"
        if all(isinstance(x, str) for x in v):
            return "[" + ", ".join(snbt2(x) for x in v) + "]"
        return "[\n" + "\n".join(t * (ind + 1) + snbt2(x, ind + 1) for x in v) + "\n" + t * ind + "]"
    if isinstance(v, dict):
        if not v:
            return "{ }"
        lines = []
        for k in sorted(v):
            key = k if all(c.isalnum() or c in "_." for c in k) else snbt2(k)
            lines.append(t * (ind + 1) + f"{key}: " + snbt2(v[k], ind + 1))
        return "{\n" + "\n".join(lines) + "\n" + t * ind + "}"
    raise TypeError(v)


def main():
    all_keys = {q.key for c in CHAPTERS for q in c["quests"]}
    ids = [qid(k) for k in all_keys]
    assert len(set(ids)) == len(ids), "id clash"
    chdir = os.path.join(ROOT, "chapters")
    shutil.rmtree(chdir, ignore_errors=True)
    os.makedirs(chdir)
    lang = {"ru_ru": {}, "en_us": {}}
    group_id = qid("group/main")
    lang["ru_ru"][f"chapter_group.{group_id}.title"] = "The Pale"
    lang["en_us"][f"chapter_group.{group_id}.title"] = "The Pale"
    for index, c in enumerate(CHAPTERS):
        cid = qid("chapter/" + c["key"])
        lang["ru_ru"][f"chapter.{cid}.title"] = c["ru"]
        lang["en_us"][f"chapter.{cid}.title"] = c["en"]
        lang["ru_ru"][f"chapter.{cid}.chapter_subtitle"] = [c["ru_sub"]]
        lang["en_us"][f"chapter.{cid}.chapter_subtitle"] = [c["en_sub"]]
        quests = []
        for q in c["quests"]:
            for d in q.deps:
                assert d in all_keys, f"{q.key}: unknown dependency {d}"
            id_ = qid(q.key)
            tasks = []
            for n, task in enumerate(q.tasks):
                task = dict(task, id=qid(f"{q.key}/task{n}"))
                tasks.append(fix_numbers(task, "task"))
            rewards = []
            for n, r in enumerate(q.rewards):
                r = dict(r, id=qid(f"{q.key}/reward{n}"))
                rewards.append(fix_numbers(r, "reward"))
            quest = {"id": id_, "x": float(q.x) * c["scale"], "y": float(q.y) * c["scale"], "tasks": tasks, "rewards": rewards}
            if q.deps:
                quest["dependencies"] = [qid(d) for d in q.deps]
            if q.icon:
                quest["icon"] = {"id": q.icon, "count": Int(1)}
            if q.shape:
                quest["shape"] = q.shape
            if q.size:
                quest["size"] = float(q.size)
            if q.extra:
                quest.update(q.extra)
            quests.append(quest)
            lang["ru_ru"][f"quest.{id_}.title"] = q.ru
            lang["en_us"][f"quest.{id_}.title"] = q.en
            if q.ru_d:
                lang["ru_ru"][f"quest.{id_}.quest_desc"] = [q.ru_d]
            if q.en_d:
                lang["en_us"][f"quest.{id_}.quest_desc"] = [q.en_d]
        doc = {
            "default_hide_dependency_lines": False,
            "default_quest_shape": "",
            "filename": c["key"],
            "group": group_id,
            "icon": {"id": c["icon"], "count": Int(1)},
            "id": cid,
            "images": [],
            "order_index": Int(index),
            "quest_links": [],
            "quests": quests,
        }
        with open(os.path.join(chdir, c["key"] + ".snbt"), "w", encoding="utf-8") as f:
            f.write(snbt2(doc) + "\n")
    tdir = os.path.join(ROOT, "reward_tables")
    shutil.rmtree(tdir, ignore_errors=True)
    os.makedirs(tdir)
    for index, t in enumerate(TABLES):
        tid = qid("table/" + t["key"])
        lang["ru_ru"][f"reward_table.{tid}.title"] = t["ru"]
        lang["en_us"][f"reward_table.{tid}.title"] = t["en"]
        rewards = [fix_numbers(dict(r, id=qid(f"table/{t['key']}/reward{n}")), "reward") for n, r in enumerate(t["rewards"])]
        doc = {"id": tid, "loot_size": Int(1), "order_index": Int(index), "rewards": rewards}
        with open(os.path.join(tdir, t["key"] + ".snbt"), "w", encoding="utf-8") as f:
            f.write(snbt2(doc) + "\n")
    with open(os.path.join(ROOT, "chapter_groups.snbt"), "w", encoding="utf-8") as f:
        f.write(snbt2({"chapter_groups": [{"id": group_id}]}) + "\n")
    os.makedirs(os.path.join(ROOT, "lang"), exist_ok=True)
    for code, entries in lang.items():
        with open(os.path.join(ROOT, "lang", code + ".snbt"), "w", encoding="utf-8") as f:
            f.write(snbt2(entries) + "\n")
    print(f"{len(CHAPTERS)} chapters, {len(all_keys)} quests, {len(TABLES)} reward tables")


if __name__ == "__main__":
    main()
