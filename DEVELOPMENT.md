# Advanced Armor 开发文档

本文档面向本项目的二次开发，描述当前源码的模块边界、CBC 穿甲接入、动态韧性计算、爆炸能量传播、Valkyrien Skies 坐标转换、数据包接口和测试方法。

## 1. 项目定位

Advanced Armor 是 Minecraft Java 1.20.1 Forge 模组，依赖 Create Big Cannons（CBC）和 Valkyrien Skies 2（VS）。项目有三条主要功能链：

1. **装甲属性链**：把数据包中的硬度、韧性、抗爆性绑定到方块 ID。
2. **CBC 穿甲链**：在 CBC 的通用炮弹碰撞流程中建立命中上下文，沿炮弹方向追踪连续装甲，向 CBC 的属性查询和质量扣除提供动态结果。
3. **CBC 高爆链**：保留 CBC 已经计算出的爆炸破坏列表，只用有限能量射线移除被装甲保护的方块，不新增 CBC 原本不会破坏的方块。

普通世界和 VS 舰船共用这些物理逻辑。区别只在于射线开始前，坐标和方向是否需要转换到舰船局部坐标。

## 2. 源码结构

### 2.1 主包类

| 类 | 作用 | 关键入口 |
|---|---|---|
| `Advancedarmor` | 模组主类；注册十种装甲方块、材料物品、检测工具和创造模式物品栏；注册数据包监听器和服务器配置 | 构造函数、`register`、`addReloadListener` |
| `ArmorData` | 读取 `armor_properties` 数据包，维护 `Block -> Values` 的线程安全快照 | `apply`、`get`、`snapshot`、`replace` |
| `ArmorNetwork` | 将服务器数据包解析结果同步到客户端 | `register`、`sync`、`Sync.encode/decode/handle` |
| `ArmorPhysics` | 沿指定方向进行方块体素追踪，计算连续装甲的总韧性、当前方块韧性、路径厚度和方块数 | `trace`、`worldContact` |
| `ArmorImpactPhysics` | 集中实现 CBC 穿甲所需的动量预算、单层质量扣除和跳弹概率公式 | `penetrationBudget`、`massCost`、`bounceChance` |
| `ArmorImpactContext` | 保存一次炮弹命中的“破坏前快照”，通过 `ThreadLocal` 让 CBC 或附属炮弹读取同一方向的动态属性 | `open`、`current`、`toughness`、`hardness`、`massCost`、`close` |
| `ArmorInspection` | 为检测工具提供无副作用的方向性查询；直接复用 `ArmorPhysics.trace` | `inspect` |
| `BlastEvents` | 监听爆炸事件，只拦截服务端 CBC `ShellExplosion` | `onDetonate` |
| `BlastPropagation` | 把 CBC 原始受影响方块列表按有限能量射线过滤 | `select` |
| `Config` | 定义服务端配置：装甲追踪上限、射线数量、总能量、吸收系数、空气损失和爆炸距离 | 静态配置项 |
| `AdvancedarmorLog` | 数据包解析警告日志的内部封装 | `warn` |
| `ArmorGameTests` | GameTest 集成测试，覆盖分层装甲、边缘、斜射、质量扣除、跳弹、爆炸、数据包、检测工具和创造栏 | 各 `@GameTest` 方法 |

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
| `ArmorInspectionOverlay` | 注册准星上方的客户端 HUD。主手或副手持有 `armor_inspection_tool` 且瞄准方块时，显示方块名称、实际韧性、硬度、方块数和模拟入射角。 |

该类只在客户端发行环境加载，不参与服务端穿甲计算。

### 2.5 `mixin` 包

| Mixin | 目标 | 作用 |
|---|---|---|
| `ProjectileCollisionMixin` | CBC `AbstractCannonProjectile` | 在 `clipAndDamage` 调用穿透方法的位置打开 `ArmorImpactContext`，然后调用实际的虚方法。附属重写 `calculateBlockPenetration` 时仍可被调用。 |
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
totalToughness += block.toughness * segmentLength
totalThickness += segmentLength
blockCount += 1
```

命中方块本身的路径贡献另外保存为 `currentToughness`。一旦遇到非装甲方块，DDA 回调返回 `false`，追踪停止；后方不连续装甲不会被错误累加。

对于完整正面穿透，一个方块的路径长度约为 1；对于斜射，路径自然变长，等价于正面厚度乘以 `sec(angle)`。代码已经通过射线几何得到这段长度，调用方不能再次乘 `sec`，否则会重复放大韧性。

当达到 `Config.ARMOR_DISTANCE` 上限时，总韧性设为正无穷，表示“至少还有一整段未观测装甲”，避免把追踪上限误报成薄装甲。

### 4.5 CBC 属性查询接入

`ArmorProviderMixin` 拦截 `BlockArmorPropertiesHandler.getProperties(state)`：

- 数据包装甲：返回自定义 Provider；
- 普通方块：返回 CBC 原 Provider。

自定义 Provider 的 `toughness` 会调用 `ArmorImpactContext.toughness`。只有世界、方块位置和 `BlockState` 都与当前快照匹配时，才返回动态总韧性；脱离命中上下文时返回单块基础韧性。这保证了工具提示、其他查询和实际炮弹命中互不污染。

### 4.6 穿透门槛和硬度差

`CannonProjectileMixin.advancedarmor$gate` 重定向 CBC 的 Provider 韧性查询：

```text
gateToughness = queriedToughness * (1 + max(0, armorHardness - shellPenetration))
```

其中 `queriedToughness` 对当前命中是连续装甲动态总韧性。硬度低于或等于炮弹穿透系数时，不额外增加门槛；硬度更高时，硬度差提高穿透要求。

`armorHardness` 使用命中前 `ArmorPhysics.Profile.effectiveHardness` 的动态硬度快照。CBC Provider、穿透门槛、单层质量扣除和跳弹均读取同一快照，检测工具复用相同的路径计算。上下文之外的 Provider 查询和采掘硬度仍使用数据包中的基础硬度。例如基础硬度为 1.95、动态硬度约为 2.343、炮弹穿透系数为 2.05 时，硬度倍率约为 1.293；速度为 12、正面命中、单块韧性为 54 时，质量损耗约为 5.821，而非 4.500。

这一步只用于穿透门槛。动态路径本身已经包含斜射厚度，不应在 Provider 或附属炮弹中再次手动乘入射角系数。

### 4.7 击穿后的质量扣除

动态韧性用于“当前命中路径能否被击穿”，而 CBC 原版模式的质量扣除仍按当前被击中的完整方块计算：

```text
normalSpeed = speed * max(0, cosine)
massCost = blockToughness
         * (1 + max(0, hardness - shellPenetration))
         / normalSpeed
```

`CannonProjectileMixin.advancedarmor$singleBlockMassCost` 修改 CBC 穿透分支第一次 `setProjectileMass` 的参数。它使用当前方块的基础韧性，而不是整段动态总韧性，也不使用边缘命中点在当前体素内的短路径长度。这样每击穿一层都会逐层扣除质量，后续层使用剩余质量重新判断。

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
context.dynamicToughness(); // 连续路径总韧性
context.dynamicHardness();  // 连续路径模型计算的等效硬度
context.baseToughness();    // 当前单块基础韧性
context.massCost();         // CBC 风格的当前单层质量损耗
context.hardnessMultiplier();
context.bounceChance(baseChance);
```

脱离公共流程的自定义碰撞实现应自行包裹：

```java
try (ArmorImpactContext ignored = ArmorImpactContext.open(projectile, state, hit)) {
    // 自定义穿透计算
}
```

附属自定义的穿透公式、质量扣除、跳弹概率仍由附属负责；本模组只保证方向性属性快照和 CBC 默认大口径炮弹的接入。

## 5. 检测工具

`ArmorInspection.inspect` 是无副作用查询接口：

1. 检查命中是否是方块、方块位置是否已加载、方向是否有效。
2. 用 CBC 世界法线计算模拟入射角。
3. 如果命中方块属于 `ArmorData`，调用 `ArmorPhysics.trace`，返回动态总韧性、动态等效硬度、连续装甲数量和角度。
4. 如果不是本模组装甲，返回 CBC 普通方块 Provider 的单块属性，数量固定为 1。

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
4. 能量耗尽时记录该射线的 `stopAt` 距离，后方同一方向的 CBC 破坏列表方块会被保留。

`BlastEnergy.accounted()` 应始终满足：

```text
remaining + absorbed + dissipated == initial
```

### 6.3 与 CBC 结果合并

`BlastPropagation` 为每条射线记录停止距离，再按照每个 CBC 受影响方块的中心方向选择最接近的采样射线。如果方块中心距离不超过该射线的停止距离，保留方块；超过停止距离则移除方块。

这里的“移除”表示装甲吸收能量后保护了 CBC 原本要破坏的方块。它不是独立重算爆炸伤害，所以不会出现爆炸范围凭空增大的问题。

VS 场景会将爆炸原点、射线方向和方块中心分别转换到对应船体空间。世界静止空间始终作为 `ShipSpace.WORLD` 参与采样。

## 7. 配置项

配置文件为服务端配置 `advancedarmor-server.toml`：

| 配置项 | 默认值 | 作用 |
|---|---:|---|
| `armorTraceDistance` | 64 | 动态韧性连续装甲追踪上限；达到上限显示为无限/检测上限 |
| `blastRays` | 512 | 爆炸球面射线数量，影响空间采样密度，不改变总能量 |
| `blastEnergyScale` | 120 | 总能量缩放，公式为 `scale * radius^3` |
| `blastAbsorptionScale` | 64 | 抗爆值转化为每条射线吸收成本的系数 |
| `blastAirLoss` | 2 | 射线每格传播的空气损失 |
| `blastMaxDistance` | 64 | 高爆射线的最大传播距离 |

增加或改变公式时，应同时更新配置注释、GameTest 和本文档中的单位说明。

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

## 9. 测试和验证

使用：

```powershell
.\gradlew.bat build --offline --console=plain
.\gradlew.bat runGameTestServer --offline --console=plain
```

当前 GameTest 覆盖重点：

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

## 10. 二次开发原则

1. **不要在多个位置重复乘入射角系数。** `ArmorPhysics.trace` 已通过 DDA 路径长度计算斜射厚度。
2. **不要把动态总韧性直接用于单层质量扣除。** 动态总韧性用于穿透门槛，CBC 风格质量扣除使用当前方块基础韧性。
3. **不要在方块破坏后再扫描装甲。** 扫描必须在 `ArmorImpactContext.open` 时完成，保证一次命中使用稳定快照。
4. **不要让爆炸逻辑独立遍历方块并重新制造破坏列表。** 高爆模块只能过滤 CBC 已有列表，避免总伤害增加。
5. **保持普通方块兼容。** `ArmorData.get` 返回空时，应回退到 CBC 或 Minecraft 原逻辑。
6. **保留上下文的栈语义。** 新增临时命中逻辑必须使用 `try-with-resources`，不能直接操作 `ThreadLocal`。
7. **VS 方向和位置必须分开变换。** 位置使用 `transformPosition`，方向使用 `transformDirection`，否则平移会污染速度和法线。
8. **改动数据加载后同时验证服务端和客户端。** 服务端决定战斗属性，客户端只显示同步结果。
