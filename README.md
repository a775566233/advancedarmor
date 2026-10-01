# 高级装甲 / Advanced Armor

Minecraft Java 1.20.1 Forge 模组。提供十种舰船装甲方块、CBC 炮弹的叠层装甲计算，以及高爆弹有限能量传播。普通世界方块与 Valkyrien Skies 2 舰船方块使用同一套规则。

## 依赖与构建

需要 Forge 47、Create Big Cannons 5.8.2、Valkyrien Skies 2 2.3.0-beta.10，以及这些模组自身要求的 Create、Kotlin For Forge 和 Ritchie's Projectile Library。项目已有对应 Gradle 依赖，无需另外添加模组。

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
```

JAR 在 `build/libs/`。将它和依赖放到客户端与服务器的 `mods` 目录。

## 装甲数据包

每个 JSON 文件放在 `data/<命名空间>/armor_properties/<文件名>.json`。文件名不限；`block` 必须是已注册的方块 ID。例如 `examples/obsidian_armor_pack` 将原版黑曜石加入装甲计算。修改后执行 `/reload`；专用服务器会将数值同步给客户端。

```json
{
  "block": "minecraft:obsidian",
  "hardness": 5.0,
  "toughness": 60.0,
  "explosion_resistance": 48.0
}
```

三个值必须是非负有限数。`hardness` 同时控制采掘硬度与 CBC 的硬度判定；`toughness` 用于炮弹穿甲；`explosion_resistance` 用于普通爆炸和高爆射线吸收。若多个文件指定同一方块，按资源 ID 字典序较后的文件覆盖较前的文件。可以在数据包中覆盖自带的十个 JSON，或为其他模组已经注册的方块添加装甲属性。Minecraft 的方块注册表在游戏启动时固定；仅靠数据包不能凭空注册新的方块 ID、模型或贴图。

## 穿甲模型

命中装甲时，模组沿炮弹方向累加连续装甲的 `toughness × 射线在方块内的路径长度`，并将总值作为该次穿透门槛。斜射穿过完整装甲面时，路径长度等于正面厚度乘 `sec(入射角)`。炮弹穿透预算遵循 CBC 的法向动量形式：`质量 × 速度 × cos(入射角) × 速度加成`。若当前方块硬度高于炮弹穿透系数，门槛再乘以 `1 + 硬度差`。击穿当前方块后，质量按 CBC 原版公式逐次扣除：`当前方块完整韧性 × (1 + max(0, 硬度 - 炮弹穿透系数)) ÷ (速度 × cos(入射角))`，不因命中点靠近边缘而减少扣除；随后重新计算余下装甲。擦射跳弹概率随装甲硬度增加。炮弹可以在多层装甲中途停止。

VS 方块沿舰船局部方块坐标逐格追踪，入射速度与表面法线在世界坐标和舰船坐标之间转换。因此旋转的船体仍按自身方块排列计算厚度。

## 高爆模型

仅 CBC 的 `ShellExplosion` 使用装甲吸能射线；其他爆炸仍采用原有传播方式，但数据包指定的装甲抗爆值对它们生效。CBC 先计算原始破坏方块列表，本模组只会从该列表中移除被装甲保护的方块，绝不添加 CBC 原本炸不掉的方块。总装甲吸能预算 `E = blastEnergyScale × radius³`，均分到 `blastRays` 条 Fibonacci 球面射线。每条射线只对数据包定义的装甲逐格计入吸收：先按距离平方衰减并付空气损失，再扣除 `explosion_resistance × 穿过装甲的距离 × blastAbsorptionScale / blastRays`；预算耗尽时，原列表中该方向更远的方块得到保护。普通方块保持 CBC 原来的破坏判定，不消耗本模组的装甲预算。附近 VS 舰船与静止世界使用同一批射线预算。CBC 原有的实体伤害、特效和爆炸消息仍由 CBC 执行。

服务器配置 `world/serverconfig/advancedarmor-server.toml` 可以调整射线数、能量、吸收、空气损失与最远追踪距离。高爆弹的具体破坏范围会受 CBC 自身爆炸半径、服务器的 CBC 破坏限制和领地保护模组影响。具体数值属于游戏平衡模型，并不对应真实材料的物理单位。

## 默认材料

| 方块 ID | 韧性 | 硬度 | 抗爆 |
|---|---:|---:|---:|
| `wrought_iron` | 16 | 0.55 | 30 |
| `homogeneous_carbon_steel` | 22 | 0.80 | 31 |
| `iron_steel_composite` | 27 | 1.20 | 29 |
| `nickel_steel` | 32 | 1.05 | 34 |
| `harvey_nickel_steel` | 42 | 1.55 | 36 |
| `kc_armor` | 54 | 1.95 | 41 |
| `knc_armor` | 46 | 1.80 | 32 |
| `sts_armor` | 38 | 1.15 | 40 |
| `ducol_steel` | 24 | 0.85 | 32 |
| `british_plastic_protection` | 9 | 0.25 | 18 |

所有 ID 均在 `advancedarmor` 命名空间。每种材料各有 16×16 PNG 贴图、模型、中英文本地化与掉落表；`tools/generate_assets.py` 可重新生成资源。源码中的注释标明了坐标转换、能量账本和 CBC 接入点。
