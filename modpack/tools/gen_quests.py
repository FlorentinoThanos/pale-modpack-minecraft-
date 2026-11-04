#!/usr/bin/env python3
"""Generates the FTB Quests files of the modpack (modpack/overrides/config/ftbquests/quests).

Run from anywhere:  python3 modpack/tools/gen_quests.py
It rewrites chapters/, chapter_groups.snbt and lang/{en_us,ru_ru}.snbt. data.snbt is left alone.
Quest ids are derived from the quest keys below, so they stay the same between runs and player
progress survives a regeneration as long as a key is not renamed.
Once you start editing quests in the in-game editor, edit there instead and stop using this script.
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
            lang["ru_ru"][f"quest.{id_}.quest_desc"] = [q.ru_d]
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
