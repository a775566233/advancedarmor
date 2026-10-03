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
    public static final ForgeConfigSpec SPEC = BUILDER.build();
    private Config() {}
}
