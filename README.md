# 高级装甲 / Advanced Armor

**中文** | [English](README.en.md)

适用于 Minecraft Java 1.20.1 Forge 的舰船装甲模组。提供十种装甲方块、沿炮弹方向计算的动态韧性与非线性硬度，以及 CBC 高爆弹的有限能量传播。静止世界和 Valkyrien Skies 2 舰船使用同一套装甲规则。

源码架构、CBC 接入点与扩展接口见 [开发文档（中文）](DEVELOPMENT.md)。

## 目录

- [安装与依赖](#安装与依赖)
- [快速开始](#快速开始)
- [默认材料](#默认材料)
- [合成与材料兼容](#合成与材料兼容)
- [装甲与炮弹计算](#装甲与炮弹计算)
- [高爆模型](#高爆模型)
- [服务器配置](#服务器配置)
- [数据包扩展](#数据包扩展)
- [构建与开发](#构建与开发)
- [兼容性与常见问题](#兼容性与常见问题)
- [许可与素材署名](#许可与素材署名)

## 安装与依赖

| 项目 | 目标或构建基准 |
|---|---|
| Minecraft Java Edition | 1.20.1 |
| Java | 17 |
| 模组加载器 | Forge 47，项目使用 47.4.23 |
| Create Big Cannons（CBC） | 5.8.2 |
| Create（机械动力） | 0.5.1.j，与基准 CBC 配套 |
| Valkyrien Skies 2（VS） | 2.3.0-beta.10 |
| 其他必需依赖 | CBC / VS 所需的 Ritchie's Projectile Library、Kotlin For Forge |

1. 安装对应的 Minecraft Forge 环境。
2. 将 Advanced Armor JAR、CBC、Create、VS 及它们所需的依赖放入 `mods/`。
3. 多人游戏时，客户端和服务器均安装本模组及依赖，使用相同的本模组版本。
4. 启动游戏后，在“高级装甲”创造模式物品栏中查找方块和检测工具。

依赖不会被打包进本模组 JAR。源码项目的 Gradle 依赖用于开发运行环境，玩家安装时仍需单独安装依赖。VS 是必需依赖，即使只在静止世界中使用装甲，也需要安装。

JEI 可用于查询合成表，但不是玩家安装本模组的必要依赖。部分生存配方需要其他模组或数据包提供钢锭、镍锭；详见下文。

## 快速开始

1. 沿预计炮弹方向放置连续装甲，空气或未定义装甲属性的方块会中断本次连续追踪。
2. 获取装甲动态检测工具，物品 ID 为 `advancedarmor:armor_inspection_tool`。
3. 主手或副手持有工具，将准星对准装甲，观察不同瞄准方向下的防护值。
4. 使用 CBC 炮弹测试。每次命中都会按当时剩余的装甲重新计算。

开启命令权限时，可直接获取工具：

```mcfunction
/give @s advancedarmor:armor_inspection_tool
```

检测工具也可由 **1 个 CBC 装甲检测工具 + 1 个 KC 装甲方块** 无序合成。它沿用 CBC 检测工具贴图，显示：

| 字段 | 含义 |
|---|---|
| 实际韧性 | 当前视线穿过连续装甲的总路径韧性，包含斜射厚度 |
| 硬度 | 同一路径模型计算的动态等效硬度 |
| 方块数量 | 射线经过的连续装甲体素数量 |
| 模拟入射角度 | 相对表面法线的角度；正面为 0°，擦射接近 90° |

检测不会破坏方块，也不包含某种炮弹的穿透系数、质量预算或速度加成。视线方向与实际炮弹方向不同时，显示值与炮弹命中值可能不同。达到追踪距离上限时，韧性显示“达到检测上限”；普通非装甲方块显示 CBC 单块基础属性，数量为 1。

## 默认材料

以下为当前内置 JSON 的单块基础值。方块 ID 均需加上 `advancedarmor:` 前缀；硬度列为基础硬度，叠层命中时使用动态硬度。

| 方块 ID | 名称 | 韧性 | 基础硬度 | 抗爆 |
|---|---|---:|---:|---:|
| `wrought_iron_blocks` | 熟铁块 | 16 | 1.00 | 30 |
| `homogeneous_carbon_steel_armor` | 均质碳钢装甲 | 22 | 1.00 | 31 |
| `iron_steel_composite_armor` | 铁-钢复合装甲 | 27 | 1.20 | 29 |
| `nickel_steel_armor` | 镍钢 | 32 | 1.05 | 34 |
| `harvey_nickel_steel_armor` | Harvey 镍钢 | 42 | 1.55 | 36 |
| `kc_armor` | KC 渗碳克虏伯装甲 | 54 | 2.00 | 41 |
| `knc_armor` | KNC 无渗碳克虏伯装甲 | 46 | 1.80 | 32 |
| `sts_armor` | 均质镍铬钢 / STS / Class B | 38 | 1.15 | 40 |
| `ducol_steel_armor` | Ducol 等高强度船体钢 | 24 | 1.02 | 32 |
| `british_plastic_protection_armor` | 英式塑性防护装甲 | 9 | 1.00 | 18 |

数值属于游戏平衡模型，不对应真实材料的物理单位。服务器数据包可以覆盖这些基础值。

## 合成与材料兼容

配方内置于模组，无需额外安装数据包。使用 JEI 查看工作台摆放方式、Create 工序与加热要求。

| 产物 | 输入 | 工序 |
|---|---|---|
| 1 个熟铁锭 | 1 个 `create:iron_sheet` + 1 个火药 | 工作台无序合成，或工作盆混压 |
| 1 个熟铁板 | 1 个熟铁锭 | 机械动力冲压机在置物台上压制 |
| 1 个熟铁块 | 9 个熟铁锭 | 工作台合成 |
| 1 个均质碳钢装甲 | 1 个铁板 + 4 个钢锭 | 工作台合成，或工作盆混压 |
| 1 个铁-钢复合装甲 | 5 个熟铁板 + 1 个均质碳钢装甲 | 加热混压 |
| 2 个镍钢材料物品 | 1 个镍锭 + 1 个钢锭 | 加热搅拌 |
| 1 个镍钢装甲 | 9 个本模组镍钢材料物品 | 工作台合成 |
| 1 个 Harvey 镍钢装甲 | 1 个镍钢装甲 + 1 个煤或木炭 | 加热混压 |
| 1 个 Ducol 装甲 | 1 个铁板 + 4 个钢锭 | 加热混压 |
| 4 个英式塑性防护装甲 | 3 个花岗岩 + 1 个 `create:limestone` + 3 个闪长岩 | 工作台合成 |

KC、KNC、STS 使用 CBC 熔融钢 `createbigcannons:molten_steel`：

| 产物 | 含铬分支输入 | 无铬分支输入 | 加热要求 |
|---|---|---|---|
| 1 个 KC | 250 mB 熔融钢 + 1 镍锭 + 1 铬锭 + 1 煤或木炭 | 500 mB 熔融钢 + 1 镍锭 + 1 煤或木炭 | 超级加热 |
| 1 个 KNC | 250 mB 熔融钢 + 1 镍锭 + 1 铬锭 | 500 mB 熔融钢 + 1 镍锭 | 超级加热 |
| 1 个 STS | 250 mB 熔融钢 + 1 镍锭 + 1 铬锭 + 1 煤或木炭 | 500 mB 熔融钢 + 1 镍锭 + 1 煤或木炭 | 普通加热 |

普通加热对应 Create 的 `heated`，超级加热对应 `superheated`。材料兼容使用 `#forge:plates/iron`、`#forge:ingots/steel`、`#forge:ingots/nickel` 和 `#minecraft:coals`。本模组不提供钢锭和镍锭来源，需由其他模组或数据包补充。

**当前铬标签注意事项：** 三种条件配方检查 `#advancedarmor:advanced_armor_chrome_ingot`，但现有聚合标签文件实际定义的是 `#forge:ingots/advanced_armor_chrome_ingot`，其内容引用 `#forge:ingots/chrome_ingot` 和 `#forge:ingots/chromium_ingot`。若没有额外数据包定义前一个标签，配方会采用无铬分支，即使已安装提供铬锭的模组。

整合包可在 `data/advancedarmor/tags/items/advanced_armor_chrome_ingot.json` 中添加以下桥接标签，启用含铬分支：

```json
{
  "replace": false,
  "values": [
    { "id": "#forge:ingots/advanced_armor_chrome_ingot", "required": false }
  ]
}
```

## 装甲与炮弹计算

### 动态韧性

命中装甲时，沿炮弹方向逐格扫描连续装甲：

```text
T = sum(toughness[i] * pathLength[i])
```

`pathLength[i]` 是射线在该方块内经过的距离。斜射厚度已通过路径长度计入；完整平板的斜射路径等价于正面厚度乘 `sec(angle)`，调用方不能再乘一次。空气和普通非装甲方块会中断追踪。

### 非线性动态硬度

当前模型以最先命中的材料为基础，按后续方块硬度和位置权重计算饱和增量：

```text
S = sum((h[i] / Href) * (i - 1)), i = 1 ... n - 1
H = h[0] + Hmax * (1 - exp(-k * S))
```

索引从 0 开始，`h[0]` 是当前命中方块的基础硬度。`k`、`Hmax`、`Href` 分别对应服务器配置中的增量系数、最大增量和参考硬度。有限路径下，增量随加权和增加而逐渐趋近 `Hmax`，不是简单相加；混合材料的排列顺序也会影响结果。

**当前权重为 `i - 1`，所以第二块的硬度贡献为 0：单块和两块装甲的等效硬度都等于首块基础硬度，从第三块开始出现增量。** 硬度权重按经过的方块顺序计算，不按每块路径长度加权；韧性仍按实际路径长度累计。

### 穿透门槛与质量损耗

CBC 大口径炮弹使用同一次命中前的动态硬度快照：

```text
hardnessMultiplier = 1 + max(0, H - shellPenetration)
gate = T * hardnessMultiplier
budget = mass * speed * cos(angle) * velocityBonus
massCost = struckBlockToughness * hardnessMultiplier / (speed * cos(angle))
```

CBC 根据预算、门槛及自身碰撞条件处理穿透。击穿后按当前方块的**完整基础韧性**扣除质量，再用剩余质量和剩余装甲判断下一次命中。速度加成参与穿透预算，但不用于减小这里的质量扣除。擦射跳弹概率也读取动态硬度；CBC 保留碰撞结果、引信、破坏和特效处理。

动态硬度提高穿透要求和质量损耗，**不会直接降低炮弹的穿透系数字段**。当硬度低于或等于炮弹穿透系数时，这两个公式中的额外硬度倍率为 1。采掘硬度始终使用材料基础硬度。

### VS 舰船

舰船方块沿船体局部坐标追踪，运动方向和表面法线在船体与世界坐标之间转换。旋转船体按自身方块排列计算装甲路径，而非世界轴方向。

## 高爆模型

仅 CBC 的 `ShellExplosion` 使用有限能量装甲吸收模型。CBC 先计算原始破坏列表，本模组用射线过滤其中被装甲保护的方块；CBC 原有实体伤害、特效和消息由 CBC 执行。

```text
energyRadius = min(CBC explosion radius, blastMaxDistance / 2)
totalEnergy = blastEnergyScale * energyRadius^3
energyPerRay = totalEnergy / blastRays
absorption = explosion_resistance * pathLength * blastAbsorptionScale / blastRays
```

射线使用 Fibonacci 球面采样，逐步计入距离平方衰减、空气损失和装甲吸收。能量耗尽后，该方向后方的方块可从 CBC 破坏列表中移除，得到保护。射线只把数据包定义的装甲计入吸收；普通方块的基础破坏判定仍交给 CBC。静止世界与附近 VS 舰船共享每条射线的预算。

增加 `blastRays` 提高采样密度，不增加总能量。本模型不会新增 CBC 原本未列入的破坏方块。非 CBC 爆炸使用原有传播方式，但数据包中的抗爆值仍通过方块抗爆属性生效。实际结果同时受 CBC 的爆炸半径、破坏配置和领地保护影响。

## 服务器配置

配置文件位于世界目录的 `serverconfig/advancedarmor-server.toml`。单人游戏通常为 `saves/<世界名>/serverconfig/`，专用服务器通常为 `<世界目录>/serverconfig/`。服务端配置决定战斗计算；修改后重启世界或服务器使配置重新加载，`/reload` 用于数据包。

| 配置项 | 默认值 | 范围 | 作用 |
|---|---:|---|---|
| `armorTraceDistance` | 64 | 1-256 | 连续装甲追踪的最大路径距离 |
| `hardnessIncrementCoefficient` | 0.5 | 0.1-1 | 动态硬度增量系数 `k` |
| `maximumHardnessIncrement` | 1.0 | 0.1-1 | 动态硬度最大增量 `Hmax` |
| `fixedReferenceHardness` | 1.95 | 0.1-3 | 动态硬度参考值 `Href` |
| `blastRays` | 512 | 64-2048 | 高爆球面射线数量 |
| `blastEnergyScale` | 120.0 | 0.01-10000 | 总高爆能量系数 |
| `blastAbsorptionScale` | 64.0 | 0.01-10000 | 装甲吸收系数 |
| `blastAirLoss` | 2.0 | 0-1000 | 每格传播的空气损失系数 |
| `blastMaxDistance` | 64 | 1-128 | 高爆射线最远传播距离 |

装甲追踪达到上限时，总韧性按无限处理，避免把未扫描完的装甲误判为薄装甲。提高追踪距离和射线数量会增加计算量。

## 数据包扩展

可以覆盖内置材料，也可以为原版或其他模组已注册的方块添加装甲属性。示例数据包见 [examples/obsidian_armor_pack](examples/obsidian_armor_pack)。

将该示例目录复制到世界的 `datapacks/`，确保 `pack.mcmeta` 位于数据包根目录，然后执行 `/reload`。自定义数据包可采用以下结构：

```text
my_armor_pack/
  pack.mcmeta
  data/
    myarmor/
      armor_properties/
        obsidian.json
```

Minecraft 1.20.1 的 `pack.mcmeta`：

```json
{
  "pack": {
    "pack_format": 15,
    "description": "Custom armor properties"
  }
}
```

`obsidian.json`：

```json
{
  "block": "minecraft:obsidian",
  "hardness": 5.0,
  "toughness": 60.0,
  "explosion_resistance": 48.0
}
```

| 字段 | 作用 |
|---|---|
| `block` | 已注册的方块 ID |
| `hardness` | 材料基础硬度，用于采掘和动态硬度模型 |
| `toughness` | 单块基础韧性，用于路径累计和击穿后的质量扣除 |
| `explosion_resistance` | 方块抗爆性和高爆射线吸收 |

三个数值必须是非负有限数。路径为 `data/<命名空间>/armor_properties/<任意文件名>.json`；文件名不必与方块 ID 相同。服务器在登录和数据包重载时将属性同步到客户端。

覆盖内置属性时，可用相同资源路径并提高数据包优先级。若不同资源 ID 声明同一方块，解析时按资源 ID 字典序较后的声明覆盖较前的声明。无效数据和未知方块会在日志中记录警告。数据包不能凭空注册新方块、物品、模型或贴图。

## 构建与开发

需要 JDK 17。Gradle Wrapper 已包含在项目中；首次构建需要联网下载 Gradle 和依赖。

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat runClient --console=plain
.\gradlew.bat runGameTestServer --console=plain
```

Linux / macOS 使用 `./gradlew`。依赖全部缓存后可加 `--offline`。发布 JAR 位于 `build/libs/`，文件名随 `gradle.properties` 中的版本变化。

开发客户端通过 `clientDevRuntimeOnly` 加载 JEI、Jade、输入法辅助和 KubeJS（及 Rhino、Architectury）。这些辅助依赖仅用于 `runClient` 及其 IDE 启动配置，不进入发布 JAR、主代码编译依赖或服务端运行配置。

### 资源与测试

- 装甲属性：[src/main/resources/data/advancedarmor/armor_properties](src/main/resources/data/advancedarmor/armor_properties)
- 配方：[src/main/resources/data/advancedarmor/recipes](src/main/resources/data/advancedarmor/recipes)
- 配置定义：[Config.java](src/main/java/org/wgx/advancedarmor/Config.java)
- 集成测试：[ArmorGameTests.java](src/main/java/org/wgx/advancedarmor/ArmorGameTests.java)
- 资源生成：[tools/generate_assets.py](tools/generate_assets.py)

资源生成脚本会重写部分贴图、模型、语言、属性、掉落表和配方。保留手动调整时，需同步修改生成脚本；目前脚本中的 KC 硬度仍为 `1.95`，运行它会覆盖当前 JSON 中的 `2.00`。

测试源码包含 18 项 GameTest，覆盖连续装甲、斜射、边缘命中、质量扣除、动态硬度、跳弹、爆炸、数据包和检测工具。此前在 CBC 5.8.2 和 `createbigcannons-5.8.3edited.jar` 上均通过 18 项测试；这不代表整个整合包已验证。**当前 KC 基础硬度已改为 2.00，但部分测试仍断言 1.95，重新测试前需同步这些平衡数值预期。**

## 兼容性与常见问题

### 为什么硬度增加后，炮弹属性没有下降？

硬度参与穿透门槛、质量扣除和跳弹概率，炮弹的穿透系数本身不随命中衰减。当 `H <= shellPenetration` 时，门槛和质量损耗没有额外硬度倍率；当前两层模型也不会产生硬度增量。

### 为什么镍钢或钢制装甲配方无法使用？

检查是否有物品加入 `#forge:ingots/nickel` 和 `#forge:ingots/steel`，以及是否满足配方加热要求。提供同名材料的模组不一定使用相同标签。铬锭还需检查上文所述的标签命名空间。

### 为什么爆炸结果与检测工具的韧性不一致？

检测工具的韧性用于炮弹穿甲。高爆模型使用 `explosion_resistance`、射线能量与 CBC 的原始破坏列表，不能直接用韧性预测爆炸范围。

### CBC 附属炮弹是否自动兼容？

经过 CBC 的 `clipAndDamage` 公共碰撞流程，并通过 `BlockArmorPropertiesHandler.getProperties(state)` 查询匹配命中属性的附属炮弹，可读取动态韧性和动态硬度，即使其穿甲方法不调用 `super`。命中快照在方块破坏前建立；普通查询返回基础属性。

CBC 大口径炮弹使用本模组的门槛、质量扣除和跳弹接入。机炮保留 CBC 的累计方块损伤流程，但属性查询可读取动态值。附属自定义的公式仍由附属负责；其质量扣除可通过 `ArmorImpactContext.current(projectile)` 读取 `baseToughness()` 或 `massCost()` 适配。完全绕过公共碰撞或 CBC 属性查询的实现需要单独接入，详见 [开发文档](DEVELOPMENT.md)。

Mixin 接入基于方法调用位置，不依赖固定局部变量槽位。修改了目标调用或方法签名的 CBC 版本仍需单独验证。反馈问题时请附 Minecraft、Forge、CBC、VS 和本模组版本、`logs/latest.log`，以及装甲排列、炮弹类型、速度和入射方向。

## 许可与素材署名

代码、构建脚本、工具脚本、文档、语言文件及数据包使用 [MIT 许可](LICENSE-MIT.md)。贴图、模型、方块状态定义等视觉素材使用 [CC BY-NC-SA 4.0](LICENSE-CCBYNCSA.md)。文件适用范围见 [LICENSE.md](LICENSE.md)；MIT 许可不覆盖视觉素材。

检测工具在运行时引用已安装 [Create Big Cannons](https://github.com/Cannoneers-of-Create/CreateBigCannons) 的原始检测器贴图，未经修改，也未将该 PNG 打包进本模组 JAR。版权归 Cannoneers of Create（Copyright (c) 2022- Cannoneers of Create）；CBC 模组作者为 rbasamoyai，上游贴图团队为 rbasamoyai、Milkyfur 和 LopyLuna。

该贴图使用 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)，须遵守署名、非商业及适用的相同方式共享条款。上游许可链接和完整署名见 [NOTICE.md](NOTICE.md)。发布 JAR 包含许可全文及署名文件。
