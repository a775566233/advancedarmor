# Advanced Armor

[Chinese](README.md) | **English**

A ship armor mod for Minecraft Java 1.20.1 Forge. Adds ten armor blocks, directional toughness, nonlinear dynamic hardness, and finite-energy propagation for CBC high-explosive shells. The same armor rules apply to world blocks and Valkyrien Skies 2 ships.

For source architecture, CBC integration points, and extension APIs, see the [development guide (Chinese)](DEVELOPMENT.md).

## Contents

- [Installation and Dependencies](#installation-and-dependencies)
- [Quick Start](#quick-start)
- [Default Materials](#default-materials)
- [Recipes and Material Compatibility](#recipes-and-material-compatibility)
- [Armor and Projectile Calculations](#armor-and-projectile-calculations)
- [High-Explosive Model](#high-explosive-model)
- [Server Configuration](#server-configuration)
- [Data Pack Extensions](#data-pack-extensions)
- [Building and Development](#building-and-development)
- [Compatibility and FAQ](#compatibility-and-faq)
- [Licensing and Attribution](#licensing-and-attribution)

## Installation and Dependencies

| Component | Target or build baseline |
|---|---|
| Minecraft Java Edition | 1.20.1 |
| Java | 17 |
| Mod loader | Forge 47; the project uses 47.4.23 |
| Create Big Cannons (CBC) | 5.8.2 |
| Create | 0.5.1.j, paired with the baseline CBC version |
| Valkyrien Skies 2 (VS) | 2.3.0-beta.10 |
| Other required dependencies | Ritchie's Projectile Library and Kotlin For Forge, as required by CBC / VS |

1. Set up the matching Minecraft Forge environment.
2. Place the Advanced Armor JAR, CBC, Create, VS, and their required dependencies in `mods/`.
3. For multiplayer, install the mod and its dependencies on both clients and the server. Use the same Advanced Armor version.
4. Find the blocks and inspection tool in the Advanced Armor creative tab.

Dependencies are not bundled in this mod's JAR. The source project's Gradle dependencies supply the development environment; players must install them separately. VS is required even when using armor only in the stationary world.

JEI is useful for viewing recipes but is not required to install this mod. Some survival recipes require another mod or data pack to supply steel and nickel ingots.

## Quick Start

1. Place contiguous armor along the expected projectile direction. Air or a block without armor properties ends the current contiguous trace.
2. Obtain the Dynamic Armor Inspection Tool: `advancedarmor:armor_inspection_tool`.
3. Hold it in either hand and aim at armor to compare protection from different directions.
4. Test with CBC projectiles. Each impact recalculates the armor remaining at that moment.

With command permissions, obtain the tool directly:

```mcfunction
/give @s advancedarmor:armor_inspection_tool
```

The tool also has a shapeless recipe: **1 CBC armor inspection tool + 1 KC armor block**. It references the CBC inspection tool texture and displays:

| Field | Meaning |
|---|---|
| Effective toughness | Total toughness along the contiguous armor path under the crosshair, including oblique thickness |
| Hardness | Dynamic effective hardness calculated for the same path |
| Block count | Number of contiguous armor voxels crossed by the ray |
| Simulated impact angle | Angle relative to the surface normal; 0 degrees is normal incidence, grazing approaches 90 degrees |

Inspection does not damage blocks or include any particular projectile's penetration rating, mass budget, or velocity bonus. If your view direction differs from the projectile's actual direction, the displayed values may differ from the impact values. At the trace distance limit, toughness displays "Trace limit reached." Ordinary blocks without armor properties show CBC's single-block base properties with a count of 1.

## Default Materials

These are the single-block base values in the current bundled JSON files. Prefix all block IDs with `advancedarmor:`. The hardness column lists base hardness; layered impacts use dynamic hardness.

| Block ID | Name | Toughness | Base hardness | Explosion resistance |
|---|---|---:|---:|---:|
| `wrought_iron_blocks` | Wrought Iron Blocks | 16 | 1.00 | 30 |
| `homogeneous_carbon_steel_armor` | Homogeneous Carbon Steel Armor | 22 | 1.00 | 31 |
| `iron_steel_composite_armor` | Iron-Steel Composite Armor | 27 | 1.20 | 29 |
| `nickel_steel_armor` | Nickel Steel | 32 | 1.05 | 34 |
| `harvey_nickel_steel_armor` | Harvey Nickel Steel | 42 | 1.55 | 36 |
| `kc_armor` | KC Cemented Armor | 54 | 2.00 | 41 |
| `knc_armor` | KNC Non-Cemented Armor | 46 | 1.80 | 32 |
| `sts_armor` | STS / Class B Armor | 38 | 1.15 | 40 |
| `ducol_steel_armor` | Ducol Hull Steel | 24 | 1.02 | 32 |
| `british_plastic_protection_armor` | British Plastic Protection | 9 | 1.00 | 18 |

These are game balance values, not physical measurements of real materials. Server data packs can override the base values.

## Recipes and Material Compatibility

Recipes are bundled with the mod; no extra data pack is needed. Use JEI to view crafting layouts, Create processing steps, and heat requirements.

| Output | Ingredients | Process |
|---|---|---|
| 1 wrought iron ingot | 1 `create:iron_sheet` + 1 gunpowder | Shapeless crafting or basin compacting |
| 1 wrought iron plate | 1 wrought iron ingot | Mechanical Press over a depot |
| 1 wrought iron block | 9 wrought iron ingots | Crafting |
| 1 homogeneous carbon steel armor | 1 iron plate + 4 steel ingots | Crafting or basin compacting |
| 1 iron-steel composite armor | 5 wrought iron plates + 1 homogeneous carbon steel armor | Heated compacting |
| 2 nickel steel material items | 1 nickel ingot + 1 steel ingot | Heated mixing |
| 1 nickel steel armor | 9 of this mod's nickel steel material items | Crafting |
| 1 Harvey nickel steel armor | 1 nickel steel armor + 1 coal or charcoal | Heated compacting |
| 1 Ducol armor | 1 iron plate + 4 steel ingots | Heated compacting |
| 4 British plastic protection blocks | 3 granite + 1 `create:limestone` + 3 diorite | Crafting |

KC, KNC, and STS use CBC molten steel, `createbigcannons:molten_steel`:

| Output | Ingredients with chromium | Ingredients without chromium | Heat |
|---|---|---|---|
| 1 KC | 250 mB molten steel + 1 nickel ingot + 1 chromium ingot + 1 coal or charcoal | 500 mB molten steel + 1 nickel ingot + 1 coal or charcoal | Superheated |
| 1 KNC | 250 mB molten steel + 1 nickel ingot + 1 chromium ingot | 500 mB molten steel + 1 nickel ingot | Superheated |
| 1 STS | 250 mB molten steel + 1 nickel ingot + 1 chromium ingot + 1 coal or charcoal | 500 mB molten steel + 1 nickel ingot + 1 coal or charcoal | Heated |

"Heated" and "superheated" refer to Create's `heated` and `superheated` recipe requirements. Material compatibility uses `#forge:plates/iron`, `#forge:ingots/steel`, `#forge:ingots/nickel`, and `#minecraft:coals`. This mod does not provide a source of steel or nickel ingots; supply them through another mod or data pack.

**Current chromium tag issue:** All three conditional recipes check `#advancedarmor:advanced_armor_chrome_ingot`, but the existing aggregate tag file defines `#forge:ingots/advanced_armor_chrome_ingot`. That aggregate references `#forge:ingots/chrome_ingot` and `#forge:ingots/chromium_ingot`. Without a data pack defining the first tag, the recipes select the chromium-free branch even if a chromium-providing mod is installed.

Modpacks can enable the chromium branch by adding this bridge at `data/advancedarmor/tags/items/advanced_armor_chrome_ingot.json`:

```json
{
  "replace": false,
  "values": [
    { "id": "#forge:ingots/advanced_armor_chrome_ingot", "required": false }
  ]
}
```

## Armor and Projectile Calculations

### Dynamic Toughness

On impact, the mod traces contiguous armor along the projectile direction:

```text
T = sum(toughness[i] * pathLength[i])
```

`pathLength[i]` is the distance traveled inside that block. Oblique thickness is already included in the path: for a complete flat plate, it is equivalent to normal thickness multiplied by `sec(angle)`. Callers must not multiply by that factor again. Air and ordinary blocks without armor properties end the trace.

### Nonlinear Dynamic Hardness

The model starts with the struck material's base hardness and adds a saturating increment weighted by the hardness and position of later blocks:

```text
S = sum((h[i] / Href) * (i - 1)), i = 1 ... n - 1
H = h[0] + Hmax * (1 - exp(-k * S))
```

Indices start at 0; `h[0]` is the struck block's base hardness. `k`, `Hmax`, and `Href` are the server-configured increment coefficient, maximum increment, and reference hardness. For finite paths, the increment approaches `Hmax` as the weighted sum increases. Hardness does not add linearly, and the order of different armor materials affects the result.

**The current weight is `i - 1`, so the second block contributes zero hardness: one-block and two-block paths both use the first block's base hardness. The increment starts with the third block.** Hardness weights follow the order of crossed blocks, not the path length inside each block; toughness still accumulates by actual path length.

### Penetration Gate and Mass Loss

CBC big-cannon projectiles use the same dynamic hardness snapshot taken before impact destruction:

```text
hardnessMultiplier = 1 + max(0, H - shellPenetration)
gate = T * hardnessMultiplier
budget = mass * speed * cos(angle) * velocityBonus
massCost = struckBlockToughness * hardnessMultiplier / (speed * cos(angle))
```

CBC handles penetration using the budget, gate, and its own collision conditions. After penetration, mass is debited using the struck block's **full base toughness**, then the next impact is evaluated using the remaining mass and armor. The velocity bonus helps the penetration budget but does not reduce this mass debit. Grazing ricochet probability also uses dynamic hardness. CBC retains control over impact results, fuzes, destruction, and effects.

Dynamic hardness raises penetration requirements and mass loss; **it does not directly decrease the projectile's penetration rating field**. When hardness is at or below the projectile's penetration rating, the extra hardness multiplier in these two formulas is 1. Mining always uses the material's base hardness.

### VS Ships

Ship blocks are traced in ship-local coordinates, with movement directions and surface normals transformed between ship and world space. A rotated ship's armor path follows its own block arrangement rather than the world axes.

## High-Explosive Model

Only CBC `ShellExplosion` instances use the finite-energy armor absorption model. CBC calculates the initial destruction list; this mod filters protected blocks from that list. CBC continues to handle entity damage, effects, and messages.

```text
energyRadius = min(CBC explosion radius, blastMaxDistance / 2)
totalEnergy = blastEnergyScale * energyRadius^3
energyPerRay = totalEnergy / blastRays
absorption = explosion_resistance * pathLength * blastAbsorptionScale / blastRays
```

Fibonacci sphere sampling distributes the rays. Each ray accounts for inverse-square attenuation, air loss, and armor absorption. Once its energy is exhausted, blocks farther in that direction can be removed from CBC's destruction list and protected. Only data-pack-defined armor absorbs energy in this model; CBC still determines the initial destruction of ordinary blocks. World blocks and nearby VS ships share each ray's budget.

Increasing `blastRays` improves sampling density without adding energy. The model cannot add blocks to CBC's destruction list. Non-CBC explosions retain their original propagation, but data-pack explosion resistance still applies through block resistance properties. Results also depend on CBC's explosion radius, destruction settings, and protection mods.

## Server Configuration

Configuration is stored in the world's `serverconfig/advancedarmor-server.toml`. In singleplayer, this is usually `saves/<world>/serverconfig/`; on a dedicated server, it is usually `<world directory>/serverconfig/`. Server configuration controls combat calculations. Restart the world or server after editing it to reload the configuration; `/reload` is for data packs.

| Setting | Default | Range | Purpose |
|---|---:|---|---|
| `armorTraceDistance` | 64 | 1-256 | Maximum path distance for contiguous armor tracing |
| `hardnessIncrementCoefficient` | 0.5 | 0.1-1 | Dynamic hardness increment coefficient `k` |
| `maximumHardnessIncrement` | 1.0 | 0.1-1 | Maximum dynamic hardness increment `Hmax` |
| `fixedReferenceHardness` | 1.95 | 0.1-3 | Dynamic hardness reference value `Href` |
| `blastRays` | 512 | 64-2048 | Number of spherical blast rays |
| `blastEnergyScale` | 120.0 | 0.01-10000 | Total blast energy coefficient |
| `blastAbsorptionScale` | 64.0 | 0.01-10000 | Armor absorption coefficient |
| `blastAirLoss` | 2.0 | 0-1000 | Air loss coefficient per block of travel |
| `blastMaxDistance` | 64 | 1-128 | Maximum blast ray travel distance |

At the armor trace limit, total toughness is treated as infinite to avoid treating incompletely scanned armor as a thin plate. Increasing trace distances and ray counts increases computational cost.

## Data Pack Extensions

Override bundled materials or add armor properties to registered vanilla or modded blocks. See the [examples/obsidian_armor_pack](examples/obsidian_armor_pack) sample.

Copy that sample directory into the world's `datapacks/`, with `pack.mcmeta` at the data pack root, then run `/reload`. A custom data pack can use this structure:

```text
my_armor_pack/
  pack.mcmeta
  data/
    myarmor/
      armor_properties/
        obsidian.json
```

`pack.mcmeta` for Minecraft 1.20.1:

```json
{
  "pack": {
    "pack_format": 15,
    "description": "Custom armor properties"
  }
}
```

`obsidian.json`:

```json
{
  "block": "minecraft:obsidian",
  "hardness": 5.0,
  "toughness": 60.0,
  "explosion_resistance": 48.0
}
```

| Field | Purpose |
|---|---|
| `block` | Registered block ID |
| `hardness` | Material base hardness for mining and the dynamic hardness model |
| `toughness` | Single-block base toughness for path accumulation and penetration mass loss |
| `explosion_resistance` | Block explosion resistance and blast ray absorption |

All three numeric values must be finite and nonnegative. Files belong in `data/<namespace>/armor_properties/<any filename>.json`; the filename need not match the block ID. The server synchronizes properties to clients on login and data pack reload.

To override a bundled property file, use the same resource path with higher data pack priority. If different resource IDs declare the same block, declarations are parsed in resource ID order and the lexicographically later one wins. Invalid data and unknown blocks produce log warnings. Data packs cannot register new blocks, items, models, or textures.

## Building and Development

Use JDK 17. The Gradle Wrapper is included; the first build needs network access to download Gradle and dependencies.

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat runClient --console=plain
.\gradlew.bat runGameTestServer --console=plain
```

On Linux / macOS, use `./gradlew`. Add `--offline` once all dependencies are cached. Release JARs appear in `build/libs/`; the filename follows the version in `gradle.properties`.

The development client loads JEI, Jade, input-method support, and KubeJS (with Rhino and Architectury) through `clientDevRuntimeOnly`. These helper dependencies are limited to `runClient` and its IDE launch configuration. They are excluded from the release JAR, main compilation dependencies, and server run configurations.

### Resources and Tests

- Armor properties: [src/main/resources/data/advancedarmor/armor_properties](src/main/resources/data/advancedarmor/armor_properties)
- Recipes: [src/main/resources/data/advancedarmor/recipes](src/main/resources/data/advancedarmor/recipes)
- Configuration definitions: [Config.java](src/main/java/org/wgx/advancedarmor/Config.java)
- Integration tests: [ArmorGameTests.java](src/main/java/org/wgx/advancedarmor/ArmorGameTests.java)
- Asset generation: [tools/generate_assets.py](tools/generate_assets.py)

The asset generator rewrites some textures, models, localization, properties, loot tables, and recipes. Update the generator when keeping manual changes. Its KC hardness value is currently still `1.95`; running it will overwrite the current JSON value of `2.00`.

The source contains 18 GameTests covering contiguous armor, oblique and edge impacts, mass loss, dynamic hardness, ricochet, explosions, data packs, and inspection. Earlier verification passed all 18 tests with both CBC 5.8.2 and `createbigcannons-5.8.3edited.jar`; this did not verify the entire modpack. **KC base hardness has since changed to 2.00, while some tests still assert 1.95. Update those balance expectations before rerunning the suite.**

## Compatibility and FAQ

### Why do projectile properties not decrease when armor hardness increases?

Hardness affects the penetration gate, mass debit, and ricochet probability. The penetration rating itself does not decay on impact. When `H <= shellPenetration`, the gate and mass debit receive no extra hardness multiplier. The current two-block model also adds no hardness increment.

### Why can I not use nickel steel or steel armor recipes?

Check that items populate `#forge:ingots/nickel` and `#forge:ingots/steel`, and that the recipe's heat requirement is met. A mod providing a material with the same name may use different tags. For chromium, also check the namespace mismatch described above.

### Why does blast damage differ from the inspection tool's toughness value?

Inspection toughness is used for projectile penetration. The blast model uses `explosion_resistance`, ray energy, and CBC's initial destruction list. Toughness alone cannot predict the explosion's destruction range.

### Are CBC addon projectiles automatically compatible?

Addon projectiles passing through CBC's shared `clipAndDamage` collision flow and querying matching impact properties through `BlockArmorPropertiesHandler.getProperties(state)` can read dynamic toughness and hardness, even when their penetration override does not call `super`. The impact snapshot is created before block destruction; ordinary queries return base values.

CBC big-cannon projectiles use this mod's gate, mass debit, and ricochet hooks. Autocannons keep CBC's cumulative block damage flow, while property queries can read dynamic values. Addons retain control over their own formulas. For mass debit integration, use `ArmorImpactContext.current(projectile)` to access `baseToughness()` or `massCost()`. Implementations bypassing the shared collision flow or CBC property queries need explicit integration; see the [development guide](DEVELOPMENT.md).

Mixin hooks target method call sites rather than fixed local variable slots. CBC versions that change those calls or method signatures still require separate verification. When reporting an issue, include Minecraft, Forge, CBC, VS, and Advanced Armor versions, `logs/latest.log`, the armor arrangement, projectile type, speed, and impact direction.

## Licensing and Attribution

Code, build scripts, tools, documentation, localization files, and data packs use the [MIT License](LICENSE-MIT.md). Textures, models, blockstate definitions, and other visual assets use [CC BY-NC-SA 4.0](LICENSE-CCBYNCSA.md). See [LICENSE.md](LICENSE.md) for file scopes. The MIT License does not cover visual assets.

The inspection tool references the original texture from the installed [Create Big Cannons](https://github.com/Cannoneers-of-Create/CreateBigCannons) mod at runtime. Its pixels are unchanged, and the PNG is not bundled in this mod's JAR. Copyright belongs to Cannoneers of Create (Copyright (c) 2022- Cannoneers of Create). CBC credits rbasamoyai as its mod author and rbasamoyai, Milkyfur, and LopyLuna as its upstream texture team.

The texture uses [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/), requiring attribution, noncommercial use, and applicable ShareAlike terms. See [NOTICE.md](NOTICE.md) for upstream license links and full attribution. Release JARs include the license texts and attribution file.
