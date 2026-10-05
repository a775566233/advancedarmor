package org.wgx.advancedarmor;

import net.minecraftforge.common.ForgeConfigSpec;

/** Work limits and game-unit calibration are server authoritative. */
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.IntValue ARMOR_DISTANCE = BUILDER.comment("Maximum contiguous armor trace distance in blocks.")
            .defineInRange("armorTraceDistance", 64, 1, 256);
    public static final ForgeConfigSpec.IntValue BLAST_RAYS = BUILDER.comment("Uniform sphere samples; total explosion energy does not increase with this count.")
            .defineInRange("blastRays", 512, 64, 2048);
    public static final ForgeConfigSpec.DoubleValue BLAST_ENERGY = BUILDER.comment("Total blast energy E = this value * CBC explosion radius cubed.")
            .defineInRange("blastEnergyScale", 120.0, 0.01, 10000);
    public static final ForgeConfigSpec.DoubleValue BLAST_ABSORPTION = BUILDER.comment("Resistance cost per unit thickness, distributed among the rays.")
            .defineInRange("blastAbsorptionScale", 64.0, 0.01, 10000);
    public static final ForgeConfigSpec.DoubleValue AIR_LOSS = BUILDER.comment("Energy lost per block of travel, before dividing among the rays.")
            .defineInRange("blastAirLoss", 2.0, 0, 1000);
    public static final ForgeConfigSpec.IntValue BLAST_DISTANCE = BUILDER.comment("Hard limit on blast travel; normally twice CBC's explosion radius.")
            .defineInRange("blastMaxDistance", 64, 1, 128);

    public static final ForgeConfigSpec.DoubleValue HARDNESS_INCREMENT_COEFFICIENT = BUILDER.comment("Hardness increment coefficient")
            .defineInRange("hardnessIncrementCoefficient", 0.5, 0.1, 1);
    public static final ForgeConfigSpec.DoubleValue MAXIMUM_HARDNESS_INCREMENT = BUILDER.comment("Maximum hardness increment")
            .defineInRange("maximumHardnessIncrement", 1, 0.1, 1);
    public static final ForgeConfigSpec.DoubleValue FIXED_REFERENCE_HARDNESS = BUILDER.comment("Fixed reference hardness")
            .defineInRange("fixedReferenceHardness", 1.95, 0.1, 3);

    public static final ForgeConfigSpec.BooleanValue ARMOR_DAMAGE_ENABLED =
            BUILDER.comment("Enable projectile-induced armor damage.")
                    .define("armorDamageEnabled", true);

    public static final ForgeConfigSpec.IntValue ARMOR_DAMAGE_MAX_LEVEL =
            BUILDER.comment("Maximum armor damage level before the block is destroyed.")
                    .defineInRange("armorDamageMaxLevel", 8, 1, 32);

    public static final ForgeConfigSpec.DoubleValue ARMOR_DAMAGE_TOUGHNESS_EXPONENT =
            BUILDER.comment("Toughness = base * (1 - level / maxLevel)^exponent. Above 1: faster early loss, slower late loss.")
                    .defineInRange("armorDamageToughnessExponent", 1.2, 0.25, 4.0);

    public static final ForgeConfigSpec.DoubleValue ARMOR_DAMAGE_REFERENCE_IMPACT =
            BUILDER.comment("Reference impact energy in CBC internal units.")
                    .defineInRange("armorDamageReferenceImpact", 2048.0, 1.0, 10000000.0);

    public static final ForgeConfigSpec.DoubleValue ARMOR_DAMAGE_IMPACT_EXPONENT =
            BUILDER.comment("Impact energy exponent used by the damage probability model.")
                    .defineInRange("armorDamageImpactExponent", 0.75, 0.1, 3.0);

    public static final ForgeConfigSpec.DoubleValue ARMOR_DAMAGE_PENETRATION_EXPONENT =
            BUILDER.comment("Projectile penetration exponent used by the damage probability model.")
                    .defineInRange("armorDamagePenetrationExponent", 1.0, 0.1, 3.0);

    public static final ForgeConfigSpec.DoubleValue ARMOR_DAMAGE_REFERENCE_TOUGHNESS =
            BUILDER.comment("Reference armor toughness used by the damage probability model.")
                    .defineInRange("armorDamageReferenceToughness", 54.0, 0.1, 10000.0);

    public static final ForgeConfigSpec.DoubleValue ARMOR_DAMAGE_MIN_TOUGHNESS_FACTOR =
            BUILDER.comment("Minimum toughness denominator as a fraction of reference toughness. Probability multiplier is clamped to [0.25, 4].")
                    .defineInRange("armorDamageMinToughnessFactor", 0.25, 0.01, 1.0);

    public static final ForgeConfigSpec.DoubleValue ARMOR_DAMAGE_HARDNESS_BONUS_SCALE =
            BUILDER.comment("Additional damage probability scaling when penetration exceeds hardness.")
                    .defineInRange("armorDamageHardnessBonusScale", 0.5, 0.0, 4.0);

    public static final ForgeConfigSpec.DoubleValue ARMOR_DAMAGE_MAX_PROBABILITY =
            BUILDER.comment("Maximum probability of applying at least one damage level.")
                    .defineInRange("armorDamageMaxProbability", 0.85, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue ARMOR_DAMAGE_PROBABILITY_RATE =
            BUILDER.comment("Growth rate of the damage probability curve.")
                    .defineInRange("armorDamageProbabilityRate", 0.65, 0.01, 5.0);

    public static final ForgeConfigSpec.DoubleValue ARMOR_DAMAGE_EXTRA_LEVEL_PROBABILITY =
            BUILDER.comment("Maximum probability of applying additional damage levels.")
                    .defineInRange("armorDamageExtraLevelProbability", 0.55, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue ARMOR_DAMAGE_MAX_LEVELS_PER_HIT =
            BUILDER.comment("Maximum damage levels added by one projectile impact.")
                    .defineInRange("armorDamageMaxLevelsPerHit", 3, 1, 8);

    public static final ForgeConfigSpec.IntValue ARMOR_DAMAGE_DECAY_INTERVAL_TICKS =
            BUILDER.comment("Server game ticks between decreases of one damage level. A damaging hit restarts the interval; offline time does not count.")
                    .defineInRange("armorDamageDecayIntervalTicks", 6000, 20, 2592000);

    public static final ForgeConfigSpec.BooleanValue ARMOR_DAMAGE_DESTROY_AT_MAX_LEVEL =
            BUILDER.comment("Destroy at maximum damage. If false, levels are capped at maxLevel - 1 to retain positive toughness.")
                    .define("armorDamageDestroyAtMaxLevel", true);
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
