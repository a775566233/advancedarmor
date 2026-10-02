"""Generate deterministic 16x16 armor textures and resource files (Python stdlib only)."""
from pathlib import Path
import json
import random
import struct
import zlib
import gzip

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
MATERIALS = [
    ("wrought_iron_blocks", "熟铁块", "Wrought Iron Blocks", 16, .55, 30, (100, 106, 105), 1),
    ("homogeneous_carbon_steel_armor", "均质碳钢装甲", "Homogeneous Carbon Steel Armor", 22, .80, 31, (87, 102, 114), 2),
    ("iron_steel_composite_armor", "铁—钢复合装甲", "Iron-Steel Composite Armor", 27, 1.20, 29, (112, 107, 101), 3),
    ("nickel_steel_armor", "镍钢", "Nickel Steel", 32, 1.05, 34, (112, 125, 132), 4),
    ("harvey_nickel_steel_armor", "Harvey 镍钢", "Harvey Nickel Steel", 42, 1.55, 36, (91, 114, 126), 5),
    ("kc_armor", "KC 渗碳克虏伯装甲", "KC Cemented Armor", 54, 1.95, 41, (78, 103, 120), 6),
    ("knc_armor", "KNC 无渗碳克虏伯装甲", "KNC Armor", 46, 1.80, 32, (107, 112, 121), 7),
    ("sts_armor", "均质镍铬钢／STS／Class B", "STS / Class B Armor", 38, 1.15, 40, (96, 115, 119), 8),
    ("ducol_steel", "Ducol 等高强度船体钢", "Ducol Hull Steel", 24, .85, 32, (110, 112, 102), 9),
    ("british_plastic_protection", "英式塑性防护", "British Plastic Protection", 9, .25, 18, (133, 123, 105), 10),
]

def put(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def png(path, pixels):
    raw = b"".join(b"\0" + bytes(channel for pixel in row for channel in (*pixel, 255)) for row in pixels)
    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

zh, en = {}, {}
for name, chinese, english, toughness, hardness, blast, base, seed in MATERIALS:
    block_id = f"advancedarmor:{name}"
    put(ROOT / "data/advancedarmor/armor_properties" / f"{name}.json", {
        "block": block_id, "hardness": hardness, "toughness": toughness, "explosion_resistance": blast})
    put(ROOT / "assets/advancedarmor/blockstates" / f"{name}.json", {"variants": {"": {"model": f"advancedarmor:block/{name}"}}})
    put(ROOT / "assets/advancedarmor/models/block" / f"{name}.json", {
        "parent": "minecraft:block/cube_all", "textures": {"all": f"advancedarmor:block/{name}"}})
    put(ROOT / "assets/advancedarmor/models/item" / f"{name}.json", {"parent": f"advancedarmor:block/{name}"})
    put(ROOT / "data/advancedarmor/loot_tables/blocks" / f"{name}.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": block_id}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    zh[f"block.advancedarmor.{name}"] = chinese
    en[f"block.advancedarmor.{name}"] = english
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        row = []
        for x in range(16):
            noise = rng.randrange(-9, 10)
            light = 8 if (x + 2*y + seed) % 7 == 0 else 0
            seam = -25 if y in (0, 15) or x in (0, 15) else 0
            # Distinct metallurgical layers, rivets, and clad faces.
            if seed in (3, 8, 9) and y in (4, 5, 10, 11): seam -= 15
            if seed in (1, 5, 6, 7) and (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)): light += 35
            if seed == 10 and (x + y) % 6 < 2: light -= 14
            row.append(tuple(max(0, min(255, c + noise + light + seam)) for c in base))
        pixels.append(row)
    png(ROOT / "assets/advancedarmor/textures/block" / f"{name}.png", pixels)

zh["itemGroup.advancedarmor.armor"] = "高级装甲"
en["itemGroup.advancedarmor.armor"] = "Advanced Armor"
zh.update({
    "item.advancedarmor.wrought_iron_ingot": "熟铁锭",
    "item.advancedarmor.wrought_iron_plate": "熟铁板",
    "item.advancedarmor.armor_inspection_tool": "装甲动态检测工具",
    "inspection.advancedarmor.toughness": "实际韧性：",
    "inspection.advancedarmor.hardness": "硬度：",
    "inspection.advancedarmor.blocks": "方块数量：",
    "inspection.advancedarmor.angle": "模拟入射角度：",
    "inspection.advancedarmor.degrees": "%s°",
    "inspection.advancedarmor.trace_limit": "达到检测上限",
})
en.update({
    "item.advancedarmor.wrought_iron_ingot": "Wrought Iron Ingot",
    "item.advancedarmor.wrought_iron_plate": "Wrought Iron Plate",
    "item.advancedarmor.armor_inspection_tool": "Dynamic Armor Inspection Tool",
    "inspection.advancedarmor.toughness": "Effective toughness: ",
    "inspection.advancedarmor.hardness": "Hardness: ",
    "inspection.advancedarmor.blocks": "Block count: ",
    "inspection.advancedarmor.angle": "Simulated impact angle: ",
    "inspection.advancedarmor.degrees": "%s°",
    "inspection.advancedarmor.trace_limit": "Trace limit reached",
})
# CBC texture is referenced unchanged under CC BY-NC-SA 4.0; see NOTICE.md.
put(ROOT / "assets/advancedarmor/models/item/armor_inspection_tool.json", {
    "parent": "minecraft:item/generated",
    "textures": {"layer0": "createbigcannons:item/block_armor_inspection_tool"},
})
put(ROOT / "data/advancedarmor/recipes/armor_inspection_tool.json", {
    "type": "minecraft:crafting_shapeless",
    "ingredients": [{"item": "createbigcannons:block_armor_inspection_tool"}, {"item": "advancedarmor:kc_armor"}],
    "result": {"item": "advancedarmor:armor_inspection_tool"},
})
# Built-in recipes mirror the mod's registered items and Create processing chain.
put(ROOT / "data/advancedarmor/recipes/wrought_iron_ingot_crafting.json", {
    "type": "minecraft:crafting_shapeless",
    "ingredients": [{"item": "create:iron_sheet"}, {"item": "minecraft:gunpowder"}],
    "result": {"item": "advancedarmor:wrought_iron_ingot", "count": 1},
})
put(ROOT / "data/advancedarmor/recipes/wrought_iron_ingot_compacting.json", {
    "type": "create:compacting",
    "ingredients": [{"item": "create:iron_sheet"}, {"item": "minecraft:gunpowder"}],
    "results": [{"item": "advancedarmor:wrought_iron_ingot", "count": 1}],
})
put(ROOT / "data/advancedarmor/recipes/wrought_iron_plate_pressing.json", {
    "type": "create:pressing",
    "ingredients": [{"item": "advancedarmor:wrought_iron_ingot"}],
    "results": [{"item": "advancedarmor:wrought_iron_plate", "count": 1}],
})
put(ROOT / "data/advancedarmor/recipes/wrought_iron_blocks_crafting.json", {
    "type": "minecraft:crafting_shaped",
    "pattern": ["III", "III", "III"],
    "key": {"I": {"item": "advancedarmor:wrought_iron_ingot"}},
    "result": {"item": "advancedarmor:wrought_iron_blocks", "count": 1},
})
put(ROOT / "data/advancedarmor/recipes/iron_steel_composite_compacting.json", {
    "type": "create:compacting",
    "heatRequirement": "heated",
    "ingredients": [{"item": "advancedarmor:wrought_iron_plate"} for _ in range(5)]
                   + [{"tag": "forge:ingots/steel"} for _ in range(4)],
    "results": [{"item": "advancedarmor:iron_steel_composite_armor", "count": 1}],
})
put(ROOT / "assets/advancedarmor/lang/zh_cn.json", zh)
put(ROOT / "assets/advancedarmor/lang/en_us.json", en)
put(ROOT / "data/minecraft/tags/blocks/mineable/pickaxe.json", {
    "replace": False, "values": [f"advancedarmor:{material[0]}" for material in MATERIALS]})
put(ROOT / "data/minecraft/tags/blocks/needs_iron_tool.json", {
    "replace": False, "values": [f"advancedarmor:{material[0]}" for material in MATERIALS]})

# Minimal compressed NBT structure used by the Forge integration tests.
def string(text):
    data = text.encode("utf-8")
    return struct.pack(">H", len(data)) + data
def tag(kind, name, payload):
    return bytes([kind]) + string(name) + payload
structure = b"\x0a\x00\x00" + tag(3, "DataVersion", struct.pack(">i", 3465))
structure += tag(9, "size", b"\x03" + struct.pack(">iiii", 3, 8, 8, 8))
structure += tag(9, "palette", b"\x0a" + struct.pack(">i", 1) + tag(8, "Name", string("minecraft:air")) + b"\x00")
structure += tag(9, "blocks", b"\x0a" + struct.pack(">i", 0))
structure += tag(9, "entities", b"\x0a" + struct.pack(">i", 0)) + b"\x00"
(ROOT / "data/advancedarmor/structures/empty.nbt").write_bytes(gzip.compress(structure, mtime=0))
