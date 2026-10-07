# Advanced Armor 开发文档

本文档面向本项目的二次开发，描述当前源码的模块边界、CBC 穿甲接入、动态韧性与硬度、逐坐标临时损坏、伪装渲染、爆炸能量传播、Valkyrien Skies 坐标转换、数据包接口和测试方法。玩家安装与玩法见 [README](README.md)。

## 目录

- [1. 项目定位](#1-项目定位)
- [2. 源码结构](#2-源码结构)
- [3. 装甲数据模型](#3-装甲数据模型)
- [4. 动态韧性完整流程](#4-动态韧性完整流程)
- [5. 检测工具](#5-检测工具)
- [6. 高爆有限能量模型](#6-高爆有限能量模型)
- [7. 配置项](#7-配置项)
- [8. 资源和数据包](#8-资源和数据包)
- [9. 测试和验证](#9-测试和验证)
- [10. 二次开发原则](#10-二次开发原则)
- [11. 装甲伪装实现](#11-装甲伪装实现)

## 1. 项目定位

Advanced Armor 是 Minecraft Java 1.20.1 Forge 模组，依赖 Create Big Cannons（CBC）和 Valkyrien Skies 2（VS）。当前构建基准为 Java 17、Forge 47.4.23、Create 0.5.1.j、CBC 5.8.2、VS 2.3.0-beta.10。项目有五条主要功能链：

1. **装甲属性链**：把数据包中的硬度、韧性、抗爆性绑定到方块 ID。
2. **CBC 穿甲链**：在 CBC 的通用炮弹碰撞流程中建立命中上下文，沿炮弹方向追踪连续装甲，向 CBC 的属性查询和质量扣除提供动态结果。
3. **CBC 高爆链**：保留 CBC 已经计算出的爆炸破坏列表，只用有限能量射线移除被装甲保护的方块，不新增 CBC 原本不会破坏的方块。
4. **临时损坏链**：命中后对仍存在的装甲按坐标叠层，降低该方块的有效韧性，持久保存、同步裂痕并按游戏时间恢复。
5. **外观伪装链**：磨具记录目标默认状态，装甲方块实体保存外观，客户端按目标模型和视觉邻居渲染。

普通世界和 VS 舰船共用这些物理逻辑。区别只在于射线开始前，坐标和方向是否需要转换到舰船局部坐标。

## 2. 源码结构

### 2.1 主包类

| 类 | 作用 | 关键入口 |
|---|---|---|
| `Advancedarmor` | 模组主类；注册十种装甲、材料、检测工具、伪装磨具、配方序列化器、伪装方块实体和创造栏；注册数据包监听器与服务器配置 | 构造函数、`register`、`addReloadListener` |
| `ArmorBlock` | 装甲实体方块，维护 `camouflaged` 标志；向 Forge 外观查询提供伪装状态 | `newBlockEntity`、`getAppearance` |
| `CamouflageMoldItem` | 磨具目标 NBT、显示名称、可配置耐久和右键应用 | `getName`、`getMaxDamage`、`useOn` |
| `CamouflageMoldRecipe` | 空磨具与目标方块的动态无序合成 | `matches`、`assemble` |
| `CamouflageArmorBlockEntity` | 保存、同步伪装；发布模型数据快照，刷新邻近模型 | `setCamouflageState`、`appearanceAt`、`getModelData`、`load` |
| `CamouflageRenderView` | 供连接纹理、AO 和面剔除使用的视觉世界视图；真实光照委托原世界 | `getBlockState`、`getBrightness`、`getBlockTint` |
| `ArmorData` | 读取 `armor_properties` 数据包，维护 `Block -> Values` 的线程安全快照 | `apply`、`get`、`snapshot`、`replace` |
| `ArmorNetwork` | 同步材料属性、损坏等级上限和区块内逐坐标损坏状态 | `register`、`sync`、`sendDamage`、`DamageSync` |
| `BlockDamageSavedData` | 按维度持久保存逐坐标损坏；计算概率、叠层、破坏和恢复 | `get`、`applyImpact`、`tick`、`save/load` |
| `ArmorDamageState` | 服务端和客户端共用的损坏读取接口；客户端使用服务器同步倍率 | `get`、`effectiveToughness`、`updateClient` |
| `RepairEvents` | 每 20 游戏刻推进损坏恢复和裂痕刷新 | `onLevelTick` |
| `ArmorPhysics` | 沿指定方向进行方块体素追踪，计算连续装甲的总韧性、当前方块韧性、路径厚度和方块数 | `trace`、`worldContact` |
| `ArmorImpactPhysics` | 集中实现 CBC 穿甲所需的动量预算、单层质量扣除和跳弹概率公式 | `penetrationBudget`、`massCost`、`bounceChance` |
| `ArmorImpactContext` | 保存一次炮弹命中的“破坏前快照”，通过 `ThreadLocal` 让 CBC 或附属炮弹读取同一方向的动态属性 | `open`、`current`、`toughness`、`hardness`、`massCost`、`close` |
| `ArmorInspection` | 为检测工具提供无副作用的方向性查询；直接复用 `ArmorPhysics.trace` | `inspect` |
| `BlastEvents` | 监听爆炸事件，只拦截服务端 CBC `ShellExplosion` | `onDetonate` |
| `BlastPropagation` | 把 CBC 原始受影响方块列表按有限能量射线过滤 | `select` |
| `Config` | 定义服务端配置：追踪、动态硬度、高爆、临时损坏和磨具耐久，共 25 项 | 静态配置项 |
| `AdvancedarmorLog` | 数据包解析警告日志的内部封装 | `warn` |
| `ArmorGameTests` | GameTest 集成测试，覆盖分层装甲、边缘、斜射、质量扣除、跳弹、爆炸、数据包、检测工具和创造栏 | 各 `@GameTest` 方法 |
| `ArmorDamageGameTests` | 临时损坏测试，覆盖局部韧性、恢复、存储、概率、检测和命中接入 | 各 `@GameTest` 方法 |
| `CamouflageGameTests` | 视觉邻居、面剔除、光照委托、Create 外观查询及 NBT 更新测试 | 各 `@GameTest` 方法 |

### 2.2 `physics` 包

| 类 | 作用 |
|---|---|
| `VoxelRay` | Amanatides-Woo 三维体素 DDA。回调每个体素的进入距离和离开距离；同时推进相交的多个坐标轴，避免边缘或角点产生重复方块。 |
| `BlastEnergy` | 单条爆炸射线的能量账本。维护初始能量、剩余能量、装甲吸收能量和距离传播损失。 |

### 2.3 `compat` 包

| 类 | 作用 |
|---|---|
| `ShipSpace` | VS 兼容桥。通过反射调用 VS 公共 API，取得世界到舰船局部、舰船局部到世界的矩阵，并提供位置变换和方向变换。 |

反射桥的目的不是实现备用物理，而是避免在源码中直接绑定 VS 的某些客户端专用签名。VS 仍然是项目的必需依赖；如果 VS API 不可用，桥会在初始化时报告错误。

### 2.4 `client` 包

| 类 | 作用 |
|---|---|
| `ArmorInspectionOverlay` | 注册准星右下的 HUD。持检测工具瞄准时，显示真实方块名、总韧性、动态硬度、数量、角度，以及局部损坏、单块有效韧性和损失百分比。 |
| `AdvancedarmorClient` | 在客户端 MOD 总线注册伪装方块实体渲染器。 |
| `CamouflageArmorRenderer` | 以目标模型、模型数据和视觉世界视图绘制伪装外观，使用世界方块格式的缓冲。 |
| `ClientArmorDamage` | 处理损坏网络快照和区块卸载，清理客户端损坏缓存。 |

这些入口只在客户端发行环境加载，不参与服务端穿甲计算。

### 2.5 `mixin` 包

| Mixin | 目标 | 作用 |
|---|---|---|
| `ProjectileCollisionMixin` | CBC `AbstractCannonProjectile` | 在 `clipAndDamage` 调用点打开命中上下文、调用实际虚方法，返回后提交临时损坏。附属重写穿透方法时仍可被调用。 |
| `ArmorDamageRemovalMixin` | Minecraft `LevelChunk` | `setBlockState` 成功返回旧状态时，清除该坐标损坏；包括同种方块属性变化。 |
| `CannonProjectileMixin` | CBC `AbstractBigCannonProjectile` | 在 `calculateBlockPenetration` 中替换动态韧性门槛、首次质量扣除参数和随机跳弹采样。 |
| `CannonProjectileAccessor` | CBC `AbstractCannonProjectile` | Invoker，调用受保护的虚方法 `calculateBlockPenetration`、`getForces`、`getBallisticProperties`，保留附属类的动态分派。 |
| `ArmorProviderMixin` | CBC `BlockArmorPropertiesHandler` | 对数据包声明的方块返回本模组的属性 Provider；命中上下文存在时，Provider 的韧性读取动态总值。普通方块保持 CBC Provider。 |
| `SurfaceNormalMixin` | CBC `CBCUtils` | VS 方块命中时，把 CBC 返回的舰船局部法线转换为世界法线。静止世界直接使用原值。 |
| `BlockHardnessMixin` | Minecraft `BlockStateBase` | 数据包中的 `hardness` 同步覆盖方块采掘速度。 |
| `BlockExplosionMixin` | Minecraft `Block` | 数据包中的 `explosion_resistance` 覆盖 `getExplosionResistance`，因此也影响普通 Minecraft 爆炸。 |
| `ExplosionAccessor` | Minecraft `Explosion` | 读取私有爆炸半径，供高爆射线模型计算总能量。 |

`advancedarmor.mixins.json` 是这些注入的统一入口。新增 Mixin 后必须同时更新该文件，并验证专用服务器加载，因为客户端专用类不能被服务端解析。

## 3. 装甲数据模型

### 3.1 `ArmorData.Values`

每个装甲方块由三个数值描述：

```java
record Values(double hardness, double toughness, double explosionResistance)
```

- `hardness`：CBC 硬度判定、动态穿透门槛的硬度差，以及方块采掘速度。
- `toughness`：装甲的基础韧性；动态追踪时按射线在方块内经过的长度累加。
- `explosionResistance`：高爆有限能量射线的吸收成本，也覆盖普通爆炸的方块抗爆性。

### 3.2 数据包路径和加载

文件路径为：

```text
data/<命名空间>/armor_properties/<任意文件名>.json
```

文件内容：

```json
{
  "block": "minecraft:obsidian",
  "hardness": 5.0,
  "toughness": 60.0,
  "explosion_resistance": 48.0
}
```

`block` 必须是游戏启动时已经注册的方块 ID。三个数值必须是非负有限数。数据包不能凭空注册新的方块、方块物品、模型或贴图；如果要添加全新方块，仍需在 Java 注册方块，再用数据包为其添加属性。

`ArmorData.apply` 会收集所有命名空间的文件，按资源 ID 排序后写入 Map。多个文件声明同一个方块时，排序较后的文件覆盖前者。因此可以：

- 用高优先级数据包覆盖内置 `advancedarmor` 配置；
- 在自己的命名空间添加原版或其他模组方块；
- `/reload` 后让服务器重新载入并同步给客户端。

服务端是属性的权威来源。`ArmorNetwork` 在登录和数据包同步事件中发送方块 ID 与三个数值；客户端只用同步快照显示检测工具，不应从客户端配置反向修改战斗属性。

## 4. 动态韧性完整流程

### 4.1 碰撞入口

CBC 的通用流程大致为：

```text
AbstractCannonProjectile.clipAndDamage
  -> calculateBlockPenetration(context, state, hit)
  -> ImpactResult
```

`ProjectileCollisionMixin` 重定向这次调用：

```text
打开 ArmorImpactContext
  -> 通过 CannonProjectileAccessor 调用 calculateBlockPenetration
  -> finally/try-with-resources 自动恢复上一个上下文
```

上下文在 CBC 破坏方块之前建立，所以后方装甲不会因为当前方法先破坏了命中方块而消失。嵌套炮弹调用也会保存并恢复前一个 `ThreadLocal` 值。

### 4.2 命中快照

`ArmorImpactContext` 构造时缓存：

1. 当前炮弹、世界、命中位置和命中状态；
2. 当前方块的 `ArmorData.Values`；
3. CBC 弹道属性，包括质量相关的穿透系数、偏转系数和速度加成；
4. 炮弹当前运动速度加上 CBC `getForces` 的力修正；
5. 速度与表面法线的夹角余弦 `cosine`；
6. `ArmorPhysics.trace` 得到的连续装甲路径。

如果命中的方块没有 `ArmorData` 属性，`armor` 和 `profile` 为空。此时上下文仍然存在，用于屏蔽外层上下文对嵌套普通方块的影响，但普通方块不会走高级装甲逻辑。

### 4.3 方向、法线和入射角

方向约定为炮弹运动方向，也就是从炮弹指向目标方块。CBC 表面法线指向方块外侧，因此正面命中使用：

```text
cosine = clamp(-direction · normal, 0, 1)
angle = acos(cosine)
```

正面命中为 0°，擦射接近 90°。VS 舰船先把世界速度转换到舰船局部空间追踪方块；法线则由 `SurfaceNormalMixin` 转回世界空间后参与 CBC 的入射角和跳弹计算。

### 4.4 连续装甲追踪

`ArmorPhysics.trace` 的步骤：

1. 用 `ShipSpace.at` 确定命中方块所属的坐标空间。
2. 将世界方向变换为局部方向并单位化。
3. 将命中位置转换到局部坐标；如果命中点恰好在共享边界，向运动方向推进一个极小量，并把坐标限制在命中体素内部。
4. 调用 `VoxelRay.trace` 逐格前进。
5. 对每个连续装甲方块，计算 `segmentLength = exit - enter`。
6. 累加：

```text
totalToughness += block.effectiveToughness * segmentLength
totalThickness += segmentLength
blockCount += 1
```

命中方块本身的路径贡献另外保存为 `currentToughness`。一旦遇到非装甲方块，DDA 回调返回 `false`，追踪停止；后方不连续装甲不会被错误累加。

对于完整正面穿透，一个方块的路径长度约为 1；对于斜射，路径自然变长，等价于正面厚度乘以 `sec(angle)`。代码已经通过射线几何得到这段长度，调用方不能再次乘 `sec`，否则会重复放大韧性。

当达到 `Config.ARMOR_DISTANCE` 上限时，总韧性设为正无穷，表示“至少还有一整段未观测装甲”，避免把追踪上限误报成薄装甲。

#### 4.4.1 非线性动态硬度

同一次追踪按方块顺序计算硬度。索引从 0 开始：

```text
S = sum((h[i] / Href) * (i - 1)), i = 1 ... n - 1
H = h[0] + Hmax * (1 - exp(-k * S))
```

`h[i]` 是材料基础硬度，`k` 为 `hardnessIncrementCoefficient`，`Hmax` 为 `maximumHardnessIncrement`，`Href` 为 `fixedReferenceHardness`。第二块权重为 0，第三块才开始提高硬度；混合材料的排列顺序影响结果。硬度增量趋近 `Hmax`，不按路径长度加权。损坏只改变有效韧性，不改变这些基础硬度输入。

### 4.5 CBC 属性查询接入

`ArmorProviderMixin` 拦截 `BlockArmorPropertiesHandler.getProperties(state)`：

- 数据包装甲：返回自定义 Provider；
- 普通方块：返回 CBC 原 Provider。

自定义 Provider 的 `toughness` 会调用 `ArmorImpactContext.toughness`。只有世界、方块位置和 `BlockState` 都与当前快照匹配时，才返回动态总韧性；脱离命中上下文时返回该坐标当前的单块有效韧性，包含损坏倍率。

### 4.6 穿透门槛和硬度差

`CannonProjectileMixin.advancedarmor$gate` 重定向 CBC 的 Provider 韧性查询：

```text
gateToughness = queriedToughness * (1 + max(0, armorHardness - shellPenetration))
```

其中 `queriedToughness` 对当前命中是连续装甲动态总韧性。硬度低于或等于炮弹穿透系数时，不额外增加门槛；硬度更高时，硬度差提高穿透要求。

`armorHardness` 使用命中前 `ArmorPhysics.Profile.effectiveHardness` 的动态硬度快照。CBC Provider、穿透门槛、单层质量扣除和跳弹均读取同一快照，检测工具复用相同的路径计算。上下文之外的 Provider 查询和采掘硬度仍使用数据包中的基础硬度。例如假设某条路径动态硬度为 2.40、炮弹穿透系数为 2.05，则硬度倍率为 1.35；速度为 12、正面命中、单块有效韧性为 54 时，质量损耗为 6.075。

这一步只用于穿透门槛。动态路径本身已经包含斜射厚度，不应在 Provider 或附属炮弹中再次手动乘入射角系数。

### 4.7 击穿后的质量扣除

动态韧性用于“当前命中路径能否被击穿”，而 CBC 原版模式的质量扣除仍按当前被击中的完整方块计算：

```text
normalSpeed = speed * max(0, cosine)
massCost = blockToughness
         * (1 + max(0, hardness - shellPenetration))
         / normalSpeed
```

`CannonProjectileMixin.advancedarmor$singleBlockMassCost` 修改 CBC 穿透分支第一次 `setProjectileMass` 的参数。它使用当前完整方块包含损坏倍率的有效韧性，不使用整段动态总韧性或当前体素内的短路径贡献。这样每击穿一层都会逐层扣除质量，后续层使用剩余质量重新判断。

公式中的 `hardness` 使用本次命中的动态硬度；每次击穿后，下次命中重新追踪剩余连续装甲并建立新的硬度快照。

当法向速度非常小，`ArmorImpactPhysics.massCost` 返回正无穷，避免擦射因为除数接近零而获得异常质量。

### 4.8 跳弹

CBC 原方法仍负责所有跳弹分支和结果处理。Mixin 只重定向随机采样，在装甲上下文中使用：

```text
chance = baseChance
       * (1 - cosine / deflection)
       * (1 + hardness / max(1, penetration))
```

结果限制在 `[0, 1]`。若不满足偏转条件，概率为 0。一次随机采样被转换为 CBC 后续比较可识别的结果，因此不会重复消耗随机状态。

### 4.9 附属炮弹兼容

关键设计是 `ProjectileCollisionMixin` 在公共 `clipAndDamage` 调用点建立上下文，并通过 Invoker 调用虚方法。附属炮弹即使重写 `calculateBlockPenetration` 且不调用 `super`，只要仍然经过 CBC 公共碰撞流程，就能通过：

```java
BlockArmorPropertiesHandler.getProperties(state)
```

读取当前方向的动态韧性。

附属如果需要使用本模组额外接口，可以读取：

```java
ArmorImpactContext context = ArmorImpactContext.current(projectile);
if (context != null) {
    context.dynamicToughness(); // 连续路径总韧性
    context.dynamicHardness();  // 连续路径模型计算的等效硬度
    context.baseToughness();    // 当前单块基础韧性
    context.effectiveBlockToughness(); // 当前完整方块含损坏倍率的有效韧性
    context.impactDamage();     // 本次命中前保存的损坏模型输入
    context.massCost();         // CBC 风格的当前单层质量损耗
    context.hardnessMultiplier();
    context.bounceChance(baseChance);
}
```

脱离公共流程的自定义碰撞实现应自行包裹：

```java
try (ArmorImpactContext snapshot = ArmorImpactContext.open(projectile, state, hit)) {
    // 自定义穿透计算
    // 若还需要提交损坏，应在这里检查服务端、装甲上下文和 ALL_DAMAGE 条件。
}
```

这里只建立属性快照，不会自动提交损坏。若完全绕开公共碰撞且需要损坏，应参照 `ProjectileCollisionMixin`，在自定义穿透方法返回后、上下文关闭前，检查服务端、`ArmorImpactContext.current(projectile) != null` 与 CBC `ALL_DAMAGE` 条件，再调用 `BlockDamageSavedData.get(server).applyImpact(server, hit.getBlockPos(), state, snapshot.impactDamage())`。普通方块上下文不能调用 `impactDamage()`；`applyImpact` 会进一步检查命中状态是否仍相同。这里要求方法正常返回，并不要求成功击穿，未击穿的命中也可以造成损坏。已经经过公共流程时不要再次提交。

附属自定义的穿透公式、质量扣除、跳弹概率仍由附属负责；本模组提供方向性属性快照和 CBC 默认大口径炮弹的接入。

### 4.10 临时命中损坏

四个接入文件分别负责：

1. `ArmorImpactContext` 在 CBC 扣除质量或破坏方块之前，保存炮弹质量、速度、法向速度、穿透系数，以及命中方块的基础硬度和当前完整单块有效韧性。
2. `ProjectileCollisionMixin` 调用实际炮弹的穿甲方法后，用快照调用 `applyImpact`。只在服务端、CBC 允许 `ALL_DAMAGE` 且命中方块仍为原状态时提交；已经被 CBC 破坏的方块不会重新创建损坏记录。
3. `ArmorInspection` 返回方向性总韧性，同时返回命中坐标的损坏等级、等级上限、单块有效韧性和损失百分比。
4. `ArmorNetwork` 登录时同步材料数据和等级上限，观察区块时发送损坏快照，命中、恢复、移除时发送增量。包包含维度、区块、坐标、方块状态、等级、上限、韧性倍率和裂痕 ID。

`applyImpact` 的 `ImpactDamage` 是一条命中参数记录，不是伤害等级，也不是伤害数值：

```java
record ImpactDamage(double projectileMass, double speed, double normalSpeed,
                    double penetration, double hardness, double toughness, double cosine)
```

`projectileMass` 是扣除前质量；`speed` 是总速度；`normalSpeed = speed * cosine`；`penetration` 是炮弹穿透系数；`hardness` 是命中材料的基础硬度；`toughness` 是该坐标完整方块的有效韧性；`cosine` 是入射角余弦。概率使用法向动能，速度单位沿用 CBC 的每游戏刻位移单位。

模型为：

```text
E = 0.5 * projectileMass * normalSpeed^2
r = min(100, penetration / max(hardness, 1e-6))
q = clamp(Tref / max(toughness, Tref * minFactor), 0.25, 4)
I = min(1e12, (E / Eref)^a * r^b * q * (1 + bonus * max(0, r - 1)))
p = pmax * (1 - exp(-rate * I))
pExtra = extraMax * I / (1 + I)
T_effective = T_base * (1 - L / Lmax)^gamma
```

以概率 `p` 添加第一层；成功后，以 `pExtra` 连续尝试额外层，最多为 `armorDamageMaxLevelsPerHit`。质量、法向速度或穿透系数为零时不添加损坏。`gamma > 1` 表示前期韧性下降较快、后期下降较慢。

记录按维度和方块坐标保存到 `advancedarmor_block_damage.dat`；同种材料的其他坐标不受影响。路径总韧性对每个体素使用其自己的 `T_effective * segmentLength`。满层默认破坏方块；关闭满层破坏时将等级限制在 `Lmax - 1`。

每隔 `armorDamageDecayIntervalTicks` 恢复一层；成功造成损坏的命中重新计时。卸载区块不强制加载，仍按服务器游戏时间恢复；关闭服务器期间不计时。`ArmorDamageRemovalMixin` 在方块状态实际替换时立即清除记录，因此同一游戏刻内挖掉再放回同材料也会重置。

损坏指纹绑定完整 `BlockState`，而非只有方块 ID。当前属性变化也触发清理，首次伪装切换 `camouflaged=false -> true` 会清除已有损坏；已经伪装时仅修改方块实体中的材质不会触发该开关。若以后需要跨外观状态保留损坏，必须同时调整替换清理和指纹匹配规则，并增加回归测试，不能只改其中一处。

保存数据根字段为 `Entries`，每项含 `Pos`、`ExpectedState`、`Level`、`LastHitTick`；`crackId` 在加载时重新分配，不写入存档。本次碰撞使用损坏前快照，新增损坏仅作用于后续查询；损坏概率输入是单块基础硬度与有效韧性，穿透门槛则使用整段动态硬度与总韧性。

客户端按等级显示原版挖掘裂痕，阶段为 `ceil(10 * L / Lmax) - 1`，限制在 0～9；零层清除。裂痕 ID 使用负数，避免与玩家挖掘 ID 冲突。每 100 游戏刻刷新，避免原版渲染器自动移除长时间无更新的裂痕。区块卸载时清除客户端缓存；服务端保持专用服务器兼容。

网络协议为 `2`，消息 ID 0 同步材料与损坏上限，ID 1 同步损坏；联机双方需使用匹配版本。伪装使用原版方块实体同步，不占用这里的消息 ID。

## 5. 检测工具

`ArmorInspection.inspect` 是无副作用查询接口：

```java
record Result(double toughness, double hardness, int blocks, double angleDegrees,
              int damageLevel, int maxDamageLevel,
              double blockToughness, double toughnessLossPercent)
```

1. 检查命中是否是方块、方块位置是否已加载、方向是否有效。
2. 用 CBC 世界法线计算模拟入射角。
3. 如果命中方块属于 `ArmorData`，调用 `ArmorPhysics.trace`，返回动态总韧性、动态等效硬度、连续装甲数量和角度。
4. 如果不是本模组装甲，返回 CBC 普通方块 Provider 的单块属性，数量固定为 1。

装甲还显示当前方块损坏 `L/Lmax`、当前单块有效韧性和韧性损失百分比；未损坏方块显示 `0/Lmax`。这些字段是命中坐标的局部状态，方向性总韧性仍通过整条路径计算。

`ArmorInspectionOverlay` 每帧读取实际准星命中结果和相机视线方向，在准星右下调用该接口。检测工具不会破坏方块，不包含某种炮弹的质量预算或硬度差倍率，因此它显示的是方向性装甲本体参数，不是某个炮弹的最终穿透结果。

## 6. 高爆有限能量模型

### 6.1 事件边界

`BlastEvents` 只处理服务端的 CBC `ShellExplosion`。它取得 CBC 已经计算出的 `affectedBlocks`，交给 `BlastPropagation.select`，然后用筛选结果替换原列表。

因此本模组不会：

- 给 CBC 原本没有列入的方块新增破坏；
- 给范围内每个方块独立发放一次完整爆炸伤害；
- 改变 CBC 的实体伤害、爆炸特效和爆炸消息。

普通 Minecraft 爆炸不走 `BlastPropagation`，但数据包中的 `explosion_resistance` 仍通过 `BlockExplosionMixin` 影响原版抗爆性。

### 6.2 总能量和射线

爆炸半径为 `R`，有效能量半径为 `energyRadius` 时，每条射线的初始能量为：

```text
E_total = blastEnergyScale * energyRadius^3
E_ray   = E_total / blastRays
```

射线方向使用 Fibonacci 球面采样，近似均匀覆盖空间。`blastRays` 增加只会提高空间采样密度，不会增加总能量。

每条射线沿体素 DDA 前进：

1. 到达下一个装甲体素前，按距离平方衰减，并扣除空气损失。
2. 装甲体素吸收：

```text
cost = explosionResistance * segmentLength
     * blastAbsorptionScale / blastRays
```

3. `BlastEnergy.absorb` 从当前射线剩余能量中扣除吸收值。
4. 能量耗尽时记录该射线的 `stopAt` 距离，后方同一方向的方块从 CBC 破坏列表移除，在世界中存活。

`BlastEnergy.accounted()` 应始终满足：

```text
remaining + absorbed + dissipated == initial
```

### 6.3 与 CBC 结果合并

`BlastPropagation.select` 返回仍应由 CBC 破坏的方块列表。它为每条射线记录停止距离，再按照每个 CBC 受影响方块的中心方向选择最接近的采样射线。如果方块中心距离不超过该射线的停止距离，保留在破坏列表中；超过停止距离则从列表移除，在世界中存活。

列表移除表示装甲吸收能量后保护了 CBC 原本要破坏的方块。该过程不新增破坏候选。

VS 场景会将爆炸原点、射线方向和方块中心分别转换到对应船体空间。世界静止空间始终作为 `ShipSpace.WORLD` 参与采样。

## 7. 配置项

配置位于世界目录的 `serverconfig/advancedarmor-server.toml`。单人游戏通常为 `saves/<世界名>/serverconfig/`，专用服务器通常为 `<世界目录>/serverconfig/`。以下 25 项均为顶层 TOML 键，没有分组前缀；范围包含边界。修改后重开世界或服务器，`/reload` 用于数据包重载。

### 7.1 追踪、硬度和高爆

| 配置变量 | 默认值 | 最小值 | 最大值 | 描述 |
|---|---:|---:|---:|---|
| `armorTraceDistance` | 64 | 1 | 256 | 连续装甲最大路径距离；达到上限按无限韧性处理 |
| `hardnessIncrementCoefficient` | 0.5 | 0.1 | 1 | 非线性硬度增量系数 k |
| `maximumHardnessIncrement` | 1 | 0.1 | 1 | 硬度饱和增量上限 Hmax，不是总硬度上限 |
| `fixedReferenceHardness` | 1.95 | 0.1 | 3 | 硬度权重参考值 Href，不自动跟随 KC 基础硬度 |
| `blastRays` | 512 | 64 | 2048 | 球面射线采样数，不改变总能量 |
| `blastEnergyScale` | 120 | 0.01 | 10000 | 总能量系数，E = scale * energyRadius^3 |
| `blastAbsorptionScale` | 64 | 0.01 | 10000 | 抗爆值转为射线吸收成本的系数 |
| `blastAirLoss` | 2 | 0 | 1000 | 每格空气损失系数，分摊到各射线 |
| `blastMaxDistance` | 64 | 1 | 128 | 高爆最大路径距离；energyRadius = min(CBC radius, 此值 / 2) |

增加或改变公式时，应同时更新配置注释、GameTest 和本文档中的单位说明。

### 7.2 临时损坏

| 配置变量 | 默认值 | 最小值 | 最大值 | 描述 |
|---|---:|---:|---:|---|
| `armorDamageEnabled` | true | false | true | 启用炮弹命中损坏 |
| `armorDamageMaxLevel` | 8 | 1 | 32 | 损坏等级上限 |
| `armorDamageToughnessExponent` | 1.2 | 0.25 | 4 | 韧性倍率指数 gamma |
| `armorDamageReferenceImpact` | 2048 | 1 | 10000000 | 法向动能参考值 Eref，CBC 内部单位 |
| `armorDamageImpactExponent` | 0.75 | 0.1 | 3 | 法向动能指数 a |
| `armorDamagePenetrationExponent` | 1 | 0.1 | 3 | 穿透/硬度比指数 b |
| `armorDamageReferenceToughness` | 54 | 0.1 | 10000 | 参考韧性 Tref |
| `armorDamageMinToughnessFactor` | 0.25 | 0.01 | 1 | 韧性分母下限相对 Tref 的比例；q 仍限制在 0.25～4 |
| `armorDamageHardnessBonusScale` | 0.5 | 0 | 4 | 穿透超过材料硬度时的附加系数 bonus |
| `armorDamageMaxProbability` | 0.85 | 0 | 1 | 至少增加一层的概率上限 pmax |
| `armorDamageProbabilityRate` | 0.65 | 0.01 | 5 | 概率曲线增长率 rate |
| `armorDamageExtraLevelProbability` | 0.55 | 0 | 1 | 连续增加额外层的概率上限 extraMax |
| `armorDamageMaxLevelsPerHit` | 3 | 1 | 8 | 每次命中最多新增层数 |
| `armorDamageDecayIntervalTicks` | 6000 | 20 | 2592000 | 恢复一层的游戏刻间隔；默认 5 分钟，20 TPS |
| `armorDamageDestroyAtMaxLevel` | true | false | true | 满层时破坏；关闭则封顶在上限减一 |

`armorDamageMaxLevel = 1` 且关闭满层破坏时，可保留的损坏等级为 0。概率上限与每次新增等级上限是不同参数；法向动能参考值使用 CBC 内部单位，不应标为焦耳。

### 7.3 伪装磨具

| 配置变量 | 默认值 | 最小值 | 最大值 | 描述 |
|---|---:|---:|---:|---|
| `camouflageMoldDurability` | 64 | 1 | 10000 | 已填充磨具最大耐久，每次应用消耗 1 点 |

磨具 `getMaxDamage` 读取当前服务端配置，没有把最大耐久固化到物品 NBT；已有物品保留已消耗的 `Damage` 值。修改上限后要考虑旧物品剩余耐久的变化。

## 8. 资源和数据包

- `assets/advancedarmor/blockstates/`：方块状态到模型的映射。
- `assets/advancedarmor/models/block/`：方块模型。
- `assets/advancedarmor/models/item/`：物品模型。
- `assets/advancedarmor/textures/`：默认贴图。
- `assets/advancedarmor/lang/`：中英文名称和检测工具文本。
- `data/advancedarmor/armor_properties/`：装甲物理属性，是战斗逻辑的主要数据驱动入口。
- `data/advancedarmor/loot_tables/blocks/`：方块掉落表，决定挖掘或爆炸后的掉落，不参与穿甲计算。
- `data/advancedarmor/recipes/`：物品和装甲配方。
- `data/minecraft/tags/blocks/`：默认装甲方块的工具标签。
- `tools/generate_assets.py`：生成默认贴图、模型、语言、属性、掉落表和部分配方。手动修改生成结果后再次运行脚本，可能覆盖手动修改；需要长期保留的改变应同步更新脚本。

掉落表文件名必须与注册方块 ID 的最后一段一致。例如注册 ID 是 `advancedarmor:kc_armor`，掉落表必须是 `loot_tables/blocks/kc_armor.json`。

### 8.1 伪装资源映射

路径均相对于 `src/main/resources/`：

| 资源 | 用途 |
|---|---|
| `assets/advancedarmor/models/item/camouflage_mold.json` | 基础磨具模型；`CustomModelData=1` 覆盖到已填充模型 |
| `assets/advancedarmor/textures/item/disguise_template.png` | 空磨具贴图，中间绿色 |
| `assets/advancedarmor/models/item/disguise_template_burned.json` | 已填充磨具模型，必须存在以供 override 解析 |
| `assets/advancedarmor/textures/item/disguise_template_burned.png` | 已填充磨具贴图，中间红色 |
| `data/advancedarmor/recipes/camouflage_mold.json` | 动态配方入口：`{"type":"advancedarmor:camouflage_mold"}`，材料匹配由 Java 完成 |

装甲 blockstate 的 `camouflaged=false` 使用原装甲模型，`camouflaged=true` 使用 `minecraft:block/air` 作为空模型占位，再由方块实体渲染器画外观。该映射没有把世界方块换成空气。当前没有空磨具的生存获取配方。

### 8.2 生成器与数据包边界

`tools/generate_assets.py` 当前 KC 硬度仍为 `1.95`，运行后会覆盖内置 JSON 的 `2.00`；它会重写语言文件，且尚未包含新增损坏与伪装文本，运行后会丢失这些语言条目。发布前应核对生成脚本和手动维护的资源，避免将生成操作作为无条件安全的重建步骤。

给其他模组方块添加 `ArmorData` 只增加装甲计算和临时损坏能力，不会自动注入 `ArmorBlock` 的方块实体或磨具支持。配方材料兼容与铬标签桥接示例见 [README](README.md#合成与材料兼容)。

## 9. 测试和验证

使用：

```powershell
.\gradlew.bat build --offline --console=plain
.\gradlew.bat runGameTestServer --offline --console=plain
```

`build` 不自动运行 GameTest；两个命令应分别执行。第一次构建需要联网下载依赖，缓存齐全后才使用 `--offline`。发布 JAR 位于 `build/libs/`。

截至 2026-10-07，基准开发环境构建成功，专用服务器 **27 项 required GameTest 全通过**：

| 测试文件 | 数量 | 主要覆盖 |
|---|---:|---|
| [ArmorGameTests.java](src/main/java/org/wgx/advancedarmor/ArmorGameTests.java) | 18 | 连续装甲、CBC 穿透、硬度、爆炸、数据包、检测与注册 |
| [ArmorDamageGameTests.java](src/main/java/org/wgx/advancedarmor/ArmorDamageGameTests.java) | 7 | 局部损坏、恢复、清理、NBT、概率、检测与碰撞提交 |
| [CamouflageGameTests.java](src/main/java/org/wgx/advancedarmor/CamouflageGameTests.java) | 2 | 视觉邻居、玻璃面剔除、光照委托、Create 外观查询、换材与加载 |

装甲计算覆盖重点：

- 连续多层装甲的动态韧性求和；
- 边缘命中和共享边界，不得出现零厚度穿透；
- 斜射路径长度和 `sec(angle)` 等价结果；
- CBC 原版逐层质量扣除；
- 法向速度接近零时的质量扣除保护；
- 装甲硬度对门槛和跳弹的作用；
- 高爆射线能量守恒和只过滤 CBC 破坏列表；
- 普通方块与数据包新增方块；
- 附属重写穿透方法时的命中上下文；
- 动态检测工具、创造模式物品栏和注册表。

修改 CBC 相关 Mixin 后，至少应在基准 CBC 和目标修改版 CBC 上分别运行 GameTest。Mixin 目标依赖 CBC 的方法描述符；如果附属修改了方法签名或完全绕过 `clipAndDamage`，需要重新设计接入点。

### 9.1 客户端手动验证

服务器 GameTest 不会烘焙客户端连接纹理 UV，也不会验证 GPU 绘制。以下是待完成或修改渲染后应执行的检查，不能由上述 27 项通过推断画面正确：

| 场景 | 检查内容 |
|---|---|
| 空磨具与已填充磨具 | 绿色 / 红色贴图、目标名称、合成限制、耐久消耗与重进世界 |
| 未伪装装甲 | 单块、堆叠和区块重载后原模型完整可见 |
| 伪装光照 | 六个面、上下堆叠、侧面遮挡、白天 / 夜晚与附近光源；保留正常方向阴影，不能异常发黑 |
| Create 连接纹理 | 同材质装甲相邻、与真实材料混放、换材、移除、对角邻居、跨区块与重进世界后的边框变化 |
| 损坏与伪装组合 | 首次伪装的损坏重置、伪装后再命中、裂痕叠绘、恢复和检测值一致 |
| VS 与其他渲染系统 | 舰船旋转与移动、透明 / cutout 材质、光影和第三方渲染器，分别记录所用版本与结果 |

实际客户端画面、VS 舰船上的伪装渲染和第三方渲染兼容尚未由此次服务器测试验证。

## 10. 二次开发原则

1. **不要在多个位置重复乘入射角系数。** `ArmorPhysics.trace` 已通过 DDA 路径长度计算斜射厚度。
2. **不要把动态总韧性直接用于单层质量扣除。** 动态总韧性用于穿透门槛，CBC 风格质量扣除使用当前完整方块的有效韧性，包含其局部损坏倍率。
3. **不要在方块破坏后再扫描装甲。** 扫描必须在 `ArmorImpactContext.open` 时完成，保证一次命中使用稳定快照。
4. **不要让爆炸逻辑独立遍历方块并重新制造破坏列表。** 高爆模块只能过滤 CBC 已有列表，避免总伤害增加。
5. **保持普通方块兼容。** `ArmorData.get` 返回空时，应回退到 CBC 或 Minecraft 原逻辑。
6. **保留上下文的栈语义。** 新增临时命中逻辑必须使用 `try-with-resources`，不能直接操作 `ThreadLocal`。
7. **VS 方向和位置必须分开变换。** 位置使用 `transformPosition`，方向使用 `transformDirection`，否则平移会污染速度和法线。
8. **改动数据加载后同时验证服务端和客户端。** 服务端决定战斗属性，客户端只显示同步结果。
9. **外观数据与真实装甲状态分别维护。** 使用渲染视图和 Forge 外观查询提供目标状态，保持装甲的物理属性与碰撞。
10. **保持模型数据和渲染格式一致。** 连接纹理模型需要正确的视觉邻居和 `ModelData`，已计算 AO / 方向阴影的方块顶点使用 BLOCK 格式的世界着色路径。
11. **异步模型查询使用快照。** 外观变化时刷新 `ModelData` 并通知邻近模型重建，避免区块编译线程直接读取可变方块实体字段。
12. **修改配置或行为时同步文档。** 核对 `Config.java`、中英文 README、本文档与测试预期，并注明自动测试不能覆盖的客户端行为。

## 11. 装甲伪装实现

### 11.1 注册与合成

`Advancedarmor` 注册物品 `advancedarmor:camouflage_mold`、同 ID 的动态配方序列化器，以及方块实体类型 `advancedarmor:camouflage_armor`。十种内置装甲均为 `ArmorBlock implements EntityBlock`，默认 `camouflaged=false`。

`CamouflageMoldRecipe` 要求恰好两个非空槽：一个未填充磨具和一个 `BlockItem`。多余材料、重复磨具 / 方块槽、已填充磨具、`ArmorBlock`、空气、默认状态含流体或方块实体均拒绝。输出新磨具，在 `TargetState` 中通过 `NbtUtils.writeBlockState` 保存默认状态，同时设置 `CustomModelData=1`。配方不复制物品 `BlockStateTag`、方块实体 NBT 或摆放方向。

磨具堆叠数为 1。`getName` 本地化显示目标方块名；这不是独立 tooltip。空磨具不能直接应用；已填充磨具不能再次参与该合成。右键由服务端调用 `setCamouflageState`，再 `hurtAndBreak(1, player, ...)`。重复覆盖同材质也消耗耐久，创造模式遵循原版耐久规则。

### 11.2 保存与同步

`CamouflageArmorBlockEntity` 只保存外观 `BlockState`，NBT 键为 `CamouflageState`。通过 `getUpdateTag` / `getUpdatePacket` 使用原版方块实体同步，不通过 `ArmorNetwork.DamageSync`。

`setCamouflageState` 更新外观、刷新模型数据、标记存档 dirty，按有无外观同步 `camouflaged` 标志并发送方块更新。`load` 在读取 NBT 后同样刷新模型数据；`onLoad` 为已有伪装数据但尚无正确标志的旧存档补设标志。

代码调用 `setCamouflageState(null)` 可以清除伪装，当前未提供对应玩家操作。挖掉重放不会复制方块实体外观到掉落物，因此恢复原外观。

### 11.3 视觉邻居与连接纹理

`CamouflageRenderView implements BlockAndTintGetter` 包装真实世界：原点返回当前伪装状态，其他装甲坐标通过 `appearanceAt` 返回各自外观，普通方块返回真实状态。视觉流体查询也跟随外观；真实光照引擎、天空光、方块光、方向阴影、群系染色和 `ModelDataManager` 均委托原世界。

`ArmorBlock.getAppearance` 向 Forge 查询返回该坐标外观，因此普通 Create 方块也能识别相邻伪装装甲。仅在伪装渲染器内替换当前状态不足以完成双向连接，邻居和普通方块的查询也必须看到相同外观。

`CamouflageArmorBlockEntity` 用私有 `ModelProperty<BlockState> APPEARANCE` 和 `volatile ModelData` 发布快照。客户端存在 `ModelDataManager` 时，`appearanceAt` 从 manager 读取快照，供异步区块模型编译使用；无 manager 的服务端查询读取方块实体。`refreshAppearance` 调用 `requestModelDataUpdate`，客户端再发送方块更新，使周围普通模型的连接纹理数据也能重建。

### 11.4 模型与光照渲染

未伪装时由正常区块模型绘制，`CamouflageArmorRenderer` 直接返回。伪装时区块模型为空占位，由渲染器执行：

1. 取得目标 `BakedModel`，以视觉视图调用 `model.getModelData(view, pos, appearance, ModelData.EMPTY)`，让 Create 等模型计算连接纹理数据。
2. 遍历目标模型的 render types，保持稳定随机种子。
3. 调用 `dispatcher.renderBatched`，传入视觉视图、模型数据、原 render type，并启用面剔除；AO 和邻接面判断使用伪装邻居。
4. 缓冲通过 `RenderTypeHelper.getMovingBlockRenderType(renderType)` 获取，使用与原版下落方块相同的 BLOCK 顶点格式和世界着色路径。

该缓冲选择用于避免世界模型已计算 AO / 方向阴影后，又被实体着色器施加一次方向光。渲染仍保留真实环境光和各面正常明暗，不能改成全亮来掩盖问题。

### 11.5 功能边界

伪装不改变真实装甲方块 ID、碰撞、采掘或战斗属性，也不复制目标方块的功能、真实光源和方块实体渲染。非完整目标模型不会改变装甲的实体碰撞形状。

当前连接纹理接入针对 Forge 外观查询和模型数据机制，自动测试使用 `create:industrial_iron_block` 查询与普通玻璃面剔除验证状态链路。任意第三方连接纹理、透明排序、光影和自定义渲染器仍需客户端验证；损坏裂痕在伪装方块实体模型上的叠绘也属于手动验证范围。

损坏记录与伪装存储分属不同系统，但首次伪装会改变完整 `BlockState`，从而触发现有损坏清理规则，详见 [4.10 临时命中损坏](#410-临时命中损坏)。
