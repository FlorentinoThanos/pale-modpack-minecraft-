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


class IntArray(list):
    """Written as an SNBT int array: [I; 1, 2, 3]."""


def qid(key):
    return hashlib.sha1(("thepale:" + key).encode()).hexdigest()[:16].upper()


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


def book(enchant, level=1):
    return {"type": "item", "item": {"id": "minecraft:enchanted_book", "count": 1, "components": {
        "minecraft:stored_enchantments": {"levels": {enchant: level}}}}}


# ---------------------------------------------------------------- quests
# Q(key, x, y, ru_title, en_title, ru_desc, en_desc, tasks, rewards, deps, icon=None, shape=None, size=None)
class Q:
    def __init__(self, key, x, y, ru, en, ru_d, en_d, tasks, rewards, deps=(), icon=None, shape=None, size=None):
        self.__dict__.update(locals())
        del self.__dict__["self"]


CHAPTERS = []


def chapter(key, icon, ru, en, ru_sub, en_sub, quests):
    CHAPTERS.append(dict(key=key, icon=icon, ru=ru, en=en, ru_sub=ru_sub, en_sub=en_sub, quests=quests))


# ================================================================ 1. Тихий остров
chapter("mild", "minecraft:oak_sapling", "Тихий остров", "The Mild Island",
        "Начало. Здесь безопасно, пока ты не подходишь к туману.",
        "The beginning. It is safe here, as long as you stay away from the fog.", [
    Q("mild/welcome", 0, 0, "Добро пожаловать в Серость", "Welcome to the Pale",
      "Земля кончается примерно в 1250 блоках от центра. Дальше лежит серое ядовитое море и стена тумана. Где-то за ним есть ещё два острова.",
      "Land ends about 1250 blocks from the centre. Beyond it lies a grey, poisonous sea behind a wall of fog. Two more islands wait somewhere past it.",
      [check()], [give("minecraft:bread", 8)], icon="minecraft:compass", shape="hexagon", size=1.5),
    Q("mild/table", 2, 0, "Первые доски", "First Planks", "Сделай верстак.", "Craft a crafting table.",
      [item("minecraft:crafting_table")], [xp(20)], ["mild/welcome"]),
    Q("mild/stone", 4, 0, "Каменный век", "Stone Age", "Каменная кирка и печь.", "A stone pickaxe and a furnace.",
      [item("minecraft:stone_pickaxe"), item("minecraft:furnace")], [xp(30), give("minecraft:coal", 8)], ["mild/table"]),
    Q("mild/bed", 4, -2, "Пережить ночь", "Survive the Night", "Поспи в кровати.", "Sleep in a bed.",
      [adv("minecraft:adventure/sleep_in_bed")], [xp(30)], ["mild/table"], icon="minecraft:red_bed"),
    Q("mild/iron", 6, 0, "Железо", "Iron", "Найди и переплавь железо.", "Find and smelt some iron.",
      [item("minecraft:iron_ingot", 16)], [xp(50)], ["mild/stone"]),
    Q("mild/shield", 8, -2, "Щит", "Shield", "Пригодится против стрел и того, что живёт на других островах.",
      "Useful against arrows and against what lives on the other islands.",
      [item("minecraft:shield")], [xp(30)], ["mild/iron"]),
    Q("mild/armor", 8, 0, "Железный доспех", "Iron Armour", "Полный комплект железной брони.", "A full set of iron armour.",
      [item("minecraft:iron_helmet"), item("minecraft:iron_chestplate"), item("minecraft:iron_leggings"), item("minecraft:iron_boots")],
      [xp(100)], ["mild/iron"], icon="minecraft:iron_chestplate"),
    Q("mild/bucket", 8, 2, "Вода с собой", "Water to Go", "Ведро и бурдюк. На юге вода спасает от жары.",
      "A bucket and a waterskin. Down south, water saves you from the heat.",
      [item("minecraft:bucket"), item("cold_sweat:waterskin")], [xp(40)], ["mild/iron"], icon="cold_sweat:waterskin"),
    Q("mild/thermo", 10, 2, "Градусник", "Thermometer", "Cold Sweat следит за температурой тела. Градусник показывает, насколько тепло вокруг.",
      "Cold Sweat tracks your body temperature. A thermometer shows how warm it is around you.",
      [item("cold_sweat:thermometer")], [xp(50)], ["mild/bucket"]),
    Q("mild/sewing", 12, 2, "Швейный стол", "Sewing Table", "На нём шьют утепление и охлаждение прямо в броню.",
      "Sews insulation right into your armour, for heat or for cold.",
      [item("cold_sweat:sewing_table")], [xp(60)], ["mild/thermo"]),
    Q("mild/coast", 6, -4, "Край мира", "Edge of the World", "Дойди до серого берега. Дальше не ходи: в тумане яд.",
      "Walk to the grey shore. Go no further: the fog is poison.",
      [biome("thepale:the_pale")], [xp(80)], ["mild/iron"], icon="minecraft:gray_dye", shape="diamond"),
    Q("mild/village", 2, -2, "Соседи", "Neighbours", "Найди деревню.", "Find a village.",
      [structure("#minecraft:village")], [xp(50), give("minecraft:emerald", 4)], ["mild/table"], icon="minecraft:bell"),
    # Create, first steps
    Q("mild/alloy", 6, 4, "Андезитовый сплав", "Andesite Alloy", "Основа всего в Create.", "The base of everything in Create.",
      [item("create:andesite_alloy", 16)], [xp(50)], ["mild/iron"]),
    Q("mild/wheel", 8, 4, "Сила воды", "Water Power", "Водяное колесо и шестерни дают первое вращение.",
      "A water wheel and some cogs give you your first rotation.",
      [item("create:water_wheel"), item("create:cogwheel", 8), item("create:shaft", 8)], [xp(60)], ["mild/alloy"]),
    Q("mild/press", 10, 4, "Пресс", "Press", "Механический пресс делает листы из слитков.", "A mechanical press turns ingots into sheets.",
      [item("create:mechanical_press"), item("create:iron_sheet", 8)], [xp(80)], ["mild/wheel"]),
    Q("mild/mixer", 12, 4, "Миксер", "Mixer", "Миксер с чашей смешивает то, что нельзя скрафтить руками.",
      "A mixer over a basin mixes what you can't craft by hand.",
      [item("create:mechanical_mixer"), item("create:basin")], [xp(80)], ["mild/press"]),
    Q("mild/drill", 12, 6, "Автоматизация", "Automation", "Механическая дрель и пила рубят и копают сами.",
      "A mechanical drill and saw dig and chop on their own.",
      [item("create:mechanical_drill"), item("create:mechanical_saw")], [xp(100)], ["mild/press"]),
    Q("mild/diamonds", 10, 0, "Алмазы", "Diamonds", "Алмазную кирку пока не скрафтить: её рецепт в Pale Store откроется в Незере. А вот алмазы уже пригодятся.",
      "You can't craft a diamond pickaxe yet: Pale Store unlocks its recipe once you reach the Nether. The diamonds will still come in handy.",
      [item("minecraft:diamond", 5)], [xp(120)], ["mild/armor"]),
    Q("mild/obsidian", 12, 0, "Литой портал", "Cast Portal",
      "Обсидиан без алмазной кирки не добыть, а её рецепт откроется только в Незере. Поэтому портал отливают на месте: выложи форму из камня, залей лаву ведром в каждую ячейку рамки и полей сверху водой. Лава станет обсидианом прямо в рамке. Ещё готовый обсидиан бывает в разрушенных порталах.",
      "You can't mine obsidian without a diamond pickaxe, and its recipe only unlocks in the Nether. So cast the portal in place: build a stone mould, pour lava into each frame cell and pour water over it. The lava turns into obsidian right in the frame. Ruined portals also hold ready obsidian.",
      [item("minecraft:lava_bucket"), item("minecraft:water_bucket")], [xp(150), give("minecraft:flint_and_steel")], ["mild/diamonds"],
      icon="minecraft:obsidian", shape="hexagon", size=1.5),
])

# ================================================================ 2. Путь на юг
NETHER_SOUTH = area("minecraft:the_nether", -100, 0, 430, 200, 128, 140)
chapter("nether", "minecraft:netherrack", "Путь на юг", "The Way South",
        "Через Незер: 4000 блоков там всего 500.",
        "Through the Nether: 4000 blocks are only 500 there.", [
    Q("nether/enter", 0, 0, "Глубже вниз", "Deeper Down", "Зажги литой портал и войди в Незер. Pale Store сразу откроет рецепт алмазной кирки.",
      "Build a portal and step into the Nether. Pale Store unlocks the diamond pickaxe recipe right away.",
      [dim("minecraft:the_nether")], [xp(150)], ["mild/obsidian"], icon="minecraft:obsidian", shape="hexagon", size=1.5),
    Q("nether/pick", 2, -2, "Алмазная кирка", "Diamond Pickaxe", "Теперь её можно скрафтить.", "Now you can craft one.",
      [item("minecraft:diamond_pickaxe")], [xp(80)], ["nether/enter"]),
    Q("nether/quartz", 2, 2, "Кварц", "Quartz", "Нужен для механизмов Create.", "Create's machines need it.",
      [item("minecraft:quartz", 32)], [xp(60)], ["nether/enter"]),
    Q("nether/fortress", 4, 0, "Крепость", "Fortress", "Найди крепость Незера.", "Find a Nether fortress.",
      [structure("minecraft:fortress")], [xp(100)], ["nether/enter"], icon="minecraft:nether_bricks"),
    Q("nether/blaze", 6, 0, "Ифриты", "Blazes", "Убей ифритов и забери их стержни.", "Kill blazes and take their rods.",
      [kill("minecraft:blaze", 8), item("minecraft:blaze_rod", 8)], [xp(150)], ["nether/fortress"], icon="minecraft:blaze_rod"),
    Q("nether/hoglin", 4, 2, "Шкура хоглина", "Hoglin Hide", "Броня из шкуры хоглина защищает от жары. На юге она очень нужна.",
      "Armour of hoglin hide protects you from heat. You will want it in the south.",
      [item("cold_sweat:hoglin_hide", 6)], [xp(80)], ["nether/enter"]),
    Q("nether/hoglin_armor", 6, 2, "Жаростойкий доспех", "Heatproof Armour", "Скрафти нагрудник из шкуры хоглина.", "Craft a hoglin hide chestplate.",
      [item("cold_sweat:hoglin_chestplate")], [xp(100)], ["nether/hoglin"]),
    Q("nether/bastion", 4, -2, "Бастион", "Bastion", "Опасно, но там есть золото и незерит.", "Dangerous, but there is gold and netherite.",
      [structure("minecraft:bastion_remnant")], [xp(120)], ["nether/enter"], icon="minecraft:gilded_blackstone"),
    Q("nether/debris", 6, -2, "Древние обломки", "Ancient Debris", "Глубоко в Незере.", "Deep in the Nether.",
      [item("minecraft:ancient_debris", 4)], [xp(200)], ["nether/bastion"]),
    Q("nether/south", 8, 0, "Портал на юг", "Portal South", "Пройди по Незеру примерно 500 блоков на юг (ось Z в плюс) и построй там портал. Он выведет на горячий остров.",
      "Walk about 500 blocks south through the Nether (positive Z) and build a portal there. It leads to the hot island.",
      [NETHER_SOUTH], [xp(200), give("minecraft:obsidian", 10)], ["nether/blaze"], icon="minecraft:compass", shape="hexagon", size=1.5),
])

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


# ---------------------------------------------------------------- Тихий остров: разведка (y -6 .. -10)
extend("mild", [
    Q("mild/corpse", 0, -2, "Последний приют", "Last Resting Place",
      "Когда ты умираешь, вещи не рассыпаются по земле: они остаются в твоём теле (мод Corpse). Вернись к нему и открой. Не умирай в тумане Бледноты: тело останется в ядовитой воде, и достать его будет почти невозможно.",
      "When you die, your things don't scatter: they stay with your body (the Corpse mod). Go back to it and open it. Don't die in the Pale's fog: the body stays in the poisoned water, and getting it back is close to impossible.",
      [check()], [xp(20)], ["mild/welcome"], icon="minecraft:skeleton_skull"),
    Q("mild/carry", 0, 2, "Переезд", "Moving House",
      "Сядь (Shift) и нажми ПКМ пустыми руками по сундуку: Carry On позволит унести его вместе с содержимым. Так же можно носить животных. Боссов, жителей и машины носить нельзя.",
      "Sneak and right-click a chest with empty hands: Carry On lets you pick it up with everything inside. Animals too. Bosses, villagers and machines can't be carried.",
      [check()], [xp(20)], ["mild/welcome"], icon="minecraft:chest"),
    Q("mild/forest", 2, -6, "Тёмный лес", "Dark Forest", "Найди тёмный лес. Там стоят особняки.", "Find a dark forest. That's where mansions stand.",
      [biome("minecraft:dark_forest")], [xp(50)], ["mild/village"], icon="minecraft:dark_oak_sapling"),
    Q("mild/taiga", 4, -6, "Прохладный север", "The Cool North", "Северная часть острова прохладнее: там тайга.", "The north of the island is cooler: there's taiga there.",
      [biome("minecraft:taiga")], [xp(50)], ["mild/village"], icon="minecraft:spruce_sapling"),
    Q("mild/swamp", 0, -6, "Болото", "Swamp", "Найди болото. Там растёт дикий рис и живут ведьмы.", "Find a swamp. Wild rice grows there, and witches live there.",
      [biome("minecraft:swamp")], [xp(50)], ["mild/village"], icon="minecraft:lily_pad"),
    Q("mild/mansion", 2, -8, "Особняк", "Woodland Mansion", "Найди лесной особняк. Внутри разбойники, будь готов.", "Find a woodland mansion. Illagers live inside, so come prepared.",
      [structure("minecraft:mansion")], [xp(150)], ["mild/forest", "mild/armor"], icon="minecraft:dark_oak_log"),
    Q("mild/totem", 2, -10, "Тотем бессмертия", "Totem of Undying", "Его роняет призыватель из особняка. Он спасает от смерти один раз.",
      "An evoker from the mansion drops it. It saves you from death once.",
      [kill("minecraft:evoker"), item("minecraft:totem_of_undying")], [xp(200)], ["mild/mansion"]),
    Q("mild/outpost", 4, -8, "Аванпост", "Pillager Outpost", "Найди аванпост разбойников. Они стоят подальше от деревень.", "Find a pillager outpost. They keep their distance from villages.",
      [structure("minecraft:pillager_outpost"), kill("minecraft:pillager", 5)], [xp(120)], ["mild/taiga", "mild/shield"], icon="minecraft:crossbow"),
    Q("mild/raid", 4, -10, "Набег", "Raid", "Отбей набег на деревню.", "Fight off a raid on a village.",
      [adv("minecraft:adventure/hero_of_the_village")], [xp(300), give("minecraft:emerald", 16)], ["mild/outpost"], icon="minecraft:white_banner"),
    Q("mild/witch", 0, -8, "Хижина ведьмы", "Witch Hut", "Найди хижину ведьмы на болоте.", "Find a witch hut in the swamp.",
      [structure("minecraft:swamp_hut")], [xp(100)], ["mild/swamp"], icon="minecraft:cauldron"),
    Q("mild/trial", 6, -6, "Испытания", "Trial Chambers", "Найди испытательные палаты под землёй и добудь ключ испытаний.",
      "Find the trial chambers underground and get a trial key.",
      [structure("minecraft:trial_chambers"), item("minecraft:trial_key")], [xp(200)], ["mild/coast", "mild/armor"], icon="minecraft:trial_key"),
    Q("mild/breeze", 6, -8, "Вихрь", "Breeze", "Победи вихря и забери его стержень.", "Defeat a breeze and take its rod.",
      [kill("minecraft:breeze"), item("minecraft:breeze_rod", 2)], [xp(200)], ["mild/trial"], icon="minecraft:breeze_rod"),
    Q("mild/mace", 6, -10, "Булава", "Mace", "Тяжёлое ядро из хранилища и стержень вихря.", "A heavy core from a vault and a breeze rod.",
      [item("minecraft:mace")], [xp(300)], ["mild/breeze"]),
    Q("mild/monument", 8, -6, "Подводная крепость", "Ocean Monument", "Найди подводную крепость у берега. Возьми зелья подводного дыхания.",
      "Find an ocean monument off the shore. Bring water breathing potions.",
      [structure("minecraft:monument"), item("minecraft:prismarine_shard", 16)], [xp(200)], ["mild/coast"], icon="minecraft:prismarine"),
    Q("mild/elder", 8, -8, "Древний страж", "Elder Guardian", "Победи древнего стража.", "Defeat an elder guardian.",
      [kill("minecraft:elder_guardian")], [xp(300), give("minecraft:sponge", 4)], ["mild/monument"], icon="minecraft:wet_sponge"),
    Q("mild/ancient", 10, -6, "Древний город", "Ancient City", "Глубоко под островом. Не шуми: там ходит Хранитель.",
      "Deep beneath the island. Keep quiet: the Warden walks there.",
      [structure("minecraft:ancient_city"), item("minecraft:echo_shard", 4)], [xp(300)], ["mild/diamonds"], icon="minecraft:sculk_catalyst"),
    Q("mild/swift", 10, -8, "Компас восстановления", "Recovery Compass",
      "Он показывает, где ты умер в последний раз. С модом Corpse это дорога к твоим вещам. Осколки эха есть только в древних городах.",
      "It points to where you last died. With the Corpse mod that's the way back to your things. Echo shards come only from ancient cities.",
      [item("minecraft:recovery_compass")], [xp(200)], ["mild/ancient"]),
    Q("mild/farm", 2, 2, "Ферма", "Farm", "Мотыга и пшеница. Еды на островах мало.", "A hoe and some wheat. Food is scarce on the islands.",
      [item("minecraft:iron_hoe"), item("minecraft:wheat", 32)], [xp(40)], ["mild/table"], icon="minecraft:wheat"),
    Q("mild/wool", 4, 2, "Шерсть", "Wool", "Шерсть пригодится для кровати, а позже для воздушного шара.", "Wool makes a bed, and later a balloon.",
      [item("minecraft:white_wool", 16)], [xp(40)], ["mild/farm"]),
    Q("mild/copper", 6, 2, "Медь", "Copper", "Медь нужна и для Create, и для бурдюка.", "Create needs copper, and so does a waterskin.",
      [item("minecraft:copper_ingot", 32)], [xp(50)], ["mild/stone"]),
    Q("mild/zinc", 4, 4, "Цинк", "Zinc", "Цинковая руда Create залегает неглубоко. Без неё не будет латуни.", "Create's zinc ore lies shallow. Without it there is no brass.",
      [item("create:zinc_ingot", 16)], [xp(60)], ["mild/stone"]),
])

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
    Q("arch/identified", 10, 2, "Опознанный артефакт", "Identified Artifact", "Опознай артефакт на столе. Внутри одно из трёх древних зачарований.",
      "Identify the artifact on the table. It holds one of three ancient enchantments.",
      [item("betterarcheology:identified_artifact")], [xp(250)], ["arch/unidentified"], shape="hexagon", size=1.25),
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

# ---------------------------------------------------------------- Путь на юг: Незер и магистраль (y 4 .. 8)
extend("nether", [
    Q("nether/soul", 2, 4, "Долина душ", "Soul Sand Valley", "Синий огонь и песок душ.", "Blue fire and soul sand.",
      [biome("minecraft:soul_sand_valley")], [xp(80)], ["nether/enter"], icon="minecraft:soul_sand"),
    Q("nether/basalt", 4, 4, "Базальтовые дельты", "Basalt Deltas", "Самое неприятное место в Незере.", "The nastiest place in the Nether.",
      [biome("minecraft:basalt_deltas")], [xp(80)], ["nether/enter"], icon="minecraft:basalt"),
    Q("nether/warped", 0, 4, "Искажённый лес", "Warped Forest", "Здесь почти не нападают.", "Hardly anything attacks you here.",
      [biome("minecraft:warped_forest")], [xp(80)], ["nether/enter"], icon="minecraft:warped_fungus"),
    Q("nether/crimson", 0, -4, "Багровый лес", "Crimson Forest", "Хоглины пасутся в багровом лесу.", "Hoglins graze in the crimson forest.",
      [biome("minecraft:crimson_forest")], [xp(80)], ["nether/enter"], icon="minecraft:crimson_fungus"),
    Q("nether/wither_skull", 6, -4, "Черепа", "Skulls", "Черепа скелетов-иссушителей из крепости.", "Wither skeleton skulls from the fortress.",
      [kill("minecraft:wither_skeleton", 10), item("minecraft:wither_skeleton_skull", 3)], [xp(250)], ["nether/fortress"], icon="minecraft:wither_skeleton_skull"),
    Q("nether/wither", 8, -4, "Иссушитель", "The Wither", "Призови и победи Иссушителя. Подальше от дома.", "Summon and defeat the Wither. Far from home.",
      [kill("minecraft:wither")], [xp(1000), give("minecraft:golden_apple", 2)], ["nether/wither_skull"], icon="minecraft:nether_star", shape="gear", size=1.75),
    Q("nether/beacon", 10, -4, "Маяк", "Beacon", "Звезда Незера и обсидиан.", "A nether star and obsidian.",
      [item("minecraft:beacon")], [xp(300)], ["nether/wither"]),
    Q("nether/netherite", 8, -2, "Незерит", "Netherite", "Незеритовый слиток из обломков и золота.", "A netherite ingot from scrap and gold.",
      [item("minecraft:netherite_ingot")], [xp(300)], ["nether/debris"]),
    Q("nether/upgrade", 10, -2, "Улучшение", "Smithing Upgrade", "Шаблон улучшения лежит в бастионе.", "The upgrade template lies in a bastion.",
      [item("minecraft:netherite_upgrade_smithing_template")], [xp(200)], ["nether/bastion"]),
    # Южная магистраль: Create trains + Steam 'n' Rails
    Q("nether/casing", 6, 6, "Ж/д корпус", "Train Casing", "Корпус поезда делают из прочного листа.", "Train casing is made from sturdy sheets.",
      [item("create:railway_casing", 8)], [xp(150)], ["create/sturdy", "nether/south"]),
    Q("nether/track", 8, 6, "Рельсы", "Tracks", "500 блоков пути по Незеру. Steam 'n' Rails добавляет рельсы из любого дерева, есть и из чернокамня.",
      "500 blocks of track through the Nether. Steam 'n' Rails adds tracks of every wood, and blackstone ones too.",
      [item("railways:track_blackstone", 128)], [xp(200)], ["nether/casing"], icon="railways:track_blackstone"),
    Q("nether/station", 10, 6, "Станции", "Stations", "Станция у каждого портала.", "A station by each portal.",
      [item("create:track_station", 2), item("create:track_signal", 4)], [xp(150)], ["nether/track"]),
    Q("nether/train", 10, 8, "Поезд", "Train", "Собери поезд: тележка на рельсах, корпус, пульт.", "Assemble a train: a bogey on the tracks, casing, controls.",
      [item("create:controls"), adv("create:train")], [xp(300)], ["nether/station"], icon="create:controls"),
    Q("nether/semaphore", 8, 8, "Семафор", "Semaphore", "Семафор показывает, свободен ли путь.", "A semaphore shows whether the line is clear.",
      [item("railways:semaphore", 2)], [xp(80)], ["nether/station"]),
    Q("nether/handcar", 6, 8, "Дрезина", "Handcar", "Маленькая дрезина Steam 'n' Rails для проверки пути.", "Steam 'n' Rails' little handcar, for checking the line.",
      [item("railways:handcar")], [xp(100)], ["nether/track"]),
    Q("nether/schedule", 12, 8, "Расписание", "Schedule", "Дай поезду расписание, и он будет ездить сам.", "Give the train a schedule and it runs on its own.",
      [item("create:schedule"), adv("create:conductor")], [xp(300)], ["nether/train"], icon="create:schedule"),
    Q("nether/mainline", 12, 6, "Южная магистраль", "The Southern Mainline",
      "Доберись поездом по Незеру до южного портала. Через Бледноту в обычном мире рельсы не помогут: в тумане яд, и пассажир не доедет.",
      "Ride the train through the Nether to the southern portal. In the Overworld no track crosses the Pale for you: the fog is poison, and no passenger survives the trip.",
      [adv("create:conductor"), NETHER_SOUTH], [xp(500)], ["nether/train"], icon="create:track_station", shape="hexagon", size=1.5),
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
    Q("enchant/penetrating", 10, 0, "Пробивающий удар", "Penetrating Strike", "Удар проходит сквозь броню.", "The blow goes through armour.",
      [item("betterarcheology:identified_artifact")], [book("betterarcheology:penetrating_strike")], ["arch/identified", "enchant/anvil"], icon="minecraft:iron_sword"),
    Q("enchant/tunneling", 10, 1, "Проходка", "Tunneling", "Кирка копает сразу несколько блоков.", "A pickaxe digs several blocks at once.",
      [item("betterarcheology:identified_artifact")], [book("betterarcheology:tunneling")], ["arch/identified", "enchant/anvil"], icon="minecraft:diamond_pickaxe"),
])


# Chapter order in the book: each island's chapter, then the side chapters that grow out of it.
ORDER = ["mild", "kitchen", "archeology", "create", "nether", "hot", "industry", "end", "sky", "cold", "chaos", "enchant"]
CHAPTERS.sort(key=lambda c: ORDER.index(c["key"]))


# ---------------------------------------------------------------- SNBT writer
def fix_numbers(o, parent=None):
    """Turn Python ints into the right NBT kind: item counts are ints, task counts/values are longs."""
    if isinstance(o, dict):
        out = {}
        for k, v in o.items():
            if isinstance(v, bool) or not isinstance(v, int):
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


class Int(int):
    pass



class Long(int):
    pass


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
            quest = {"id": id_, "x": float(q.x), "y": float(q.y), "tasks": tasks, "rewards": rewards}
            if q.deps:
                quest["dependencies"] = [qid(d) for d in q.deps]
            if q.icon:
                quest["icon"] = {"id": q.icon, "count": Int(1)}
            if q.shape:
                quest["shape"] = q.shape
            if q.size:
                quest["size"] = float(q.size)
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
    with open(os.path.join(ROOT, "chapter_groups.snbt"), "w", encoding="utf-8") as f:
        f.write(snbt2({"chapter_groups": [{"id": group_id}]}) + "\n")
    os.makedirs(os.path.join(ROOT, "lang"), exist_ok=True)
    for code, entries in lang.items():
        with open(os.path.join(ROOT, "lang", code + ".snbt"), "w", encoding="utf-8") as f:
            f.write(snbt2(entries) + "\n")
    print(f"{len(CHAPTERS)} chapters, {len(all_keys)} quests")


if __name__ == "__main__":
    main()
