package org.wgx.advancedarmor;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.big_cannon.solid_shot.SolidShotProjectile;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

import java.util.List;

@GameTestHolder(Advancedarmor.MODID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class ArmorDamageGameTests {
    private static BlockPos plate(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) helper.setBlock(x, 2, 1, Advancedarmor.block("kc_armor").get());
        helper.setBlock(4, 2, 1, Blocks.AIR);
        return helper.absolutePos(new BlockPos(1, 2, 1));
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(new Vec3(pos.getX(), pos.getY() + .5, pos.getZ() + .5), Direction.WEST, pos, false);
    }

    private static void near(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .001, message + ": " + actual + " != " + expected);
    }

    @GameTest(template = "empty")
    public static void localDamageReducesPathAndInspection(GameTestHelper helper) {
        BlockPos front = plate(helper);
        var level = helper.getLevel();
        BlockState state = level.getBlockState(front);
        var data = BlockDamageSavedData.get(level);
        data.addDamageLevels(level, front, state, 2);
        double damaged = 54 * BlockDamageSavedData.toughnessMultiplier(2);
        near(helper, ArmorPhysics.trace(level, hit(front), new Vec3(1, 0, 0)).totalToughness(),
                damaged + 108, "Only the struck layer loses toughness");
        helper.assertTrue(data.damageLevel(front.east(), state) == 0 && data.damageLevel(front.east(2), state) == 0,
                "Backing armor must remain undamaged");
        var inspection = ArmorInspection.inspect(level, hit(front), new Vec3(1, 0, 0));
        helper.assertTrue(inspection != null && inspection.damageLevel() == 2
                && inspection.maxDamageLevel() == Config.ARMOR_DAMAGE_MAX_LEVEL.get(), "Inspection exposes current stacks");
        near(helper, inspection.blockToughness(), damaged, "Inspection exposes full block toughness");
        near(helper, inspection.toughnessLossPercent(), 100 * (1 - damaged / 54), "Inspection exposes percentage loss");
        for (int y = 3; y <= 7; y++) helper.setBlock(1, y, 1, Advancedarmor.block("kc_armor").get());
        double cosine = .25;
        double firstSegment = .5 / Math.sqrt(1 - cosine * cosine);
        near(helper, ArmorPhysics.trace(level, hit(front), new Vec3(cosine, Math.sqrt(1 - cosine * cosine), 0))
                .totalToughness(), damaged * firstSegment + 54 * (1 / cosine - firstSegment),
                "Oblique thickness includes damaged and intact segments separately");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void damagePersistsAndDecaysOneLevelAtATime(GameTestHelper helper) {
        BlockPos front = plate(helper);
        var level = helper.getLevel();
        BlockState state = level.getBlockState(front);
        var data = new BlockDamageSavedData();
        data.addDamageLevels(level, front, state, 3);
        CompoundTag root = data.save(new CompoundTag());
        var restored = BlockDamageSavedData.load(level, root);
        helper.assertTrue(restored.damageLevel(front, state) == 3, "NBT roundtrip retains position and damage");
        long interval = Config.ARMOR_DAMAGE_DECAY_INTERVAL_TICKS.get();
        root.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).putLong("LastHitTick", level.getGameTime() - interval);
        restored = BlockDamageSavedData.load(level, root);
        restored.tick(level);
        helper.assertTrue(restored.damageLevel(front, state) == 2 && restored.isDirty(), "One elapsed interval removes one level and saves it");
        root = restored.save(new CompoundTag());
        root.getList("Entries", Tag.TAG_COMPOUND).getCompound(0).putLong("LastHitTick", level.getGameTime() - 2 * interval);
        restored = BlockDamageSavedData.load(level, root);
        restored.tick(level);
        helper.assertTrue(restored.damageLevel(front, state) == 0 && restored.snapshot(new ChunkPos(front)).isEmpty(),
                "Recovery removes the entry and restores full toughness");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void replacementResetsDamageImmediately(GameTestHelper helper) {
        BlockPos front = plate(helper);
        var level = helper.getLevel();
        BlockState state = level.getBlockState(front);
        var data = BlockDamageSavedData.get(level);
        data.addDamageLevels(level, front, state, 2);
        level.setBlock(front, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(front, state, 3);
        helper.assertTrue(data.damageLevel(front, state) == 0, "Mining and replacing identical armor in one tick resets damage");
        data.addDamageLevels(level, front, state, Config.ARMOR_DAMAGE_MAX_LEVEL.get());
        helper.assertTrue(level.getBlockState(front).isAir() && data.damageLevel(front, state) == 0,
                "Maximum damage destroys the block and clears its state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void disabledDestructionRetainsPositiveToughness(GameTestHelper helper) {
        BlockPos front = plate(helper);
        var level = helper.getLevel();
        BlockState state = level.getBlockState(front);
        var data = BlockDamageSavedData.get(level);
        boolean destroy = Config.ARMOR_DAMAGE_DESTROY_AT_MAX_LEVEL.get();
        try {
            Config.ARMOR_DAMAGE_DESTROY_AT_MAX_LEVEL.set(false);
            data.addDamageLevels(level, front, state, 100);
            helper.assertTrue(!level.getBlockState(front).isAir()
                    && data.damageLevel(front, state) == Config.ARMOR_DAMAGE_MAX_LEVEL.get() - 1,
                    "No-destruction mode caps damage below the destruction level");
            helper.assertTrue(data.getEffectiveToughness(level, front, state, 54) > 0, "Surviving armor retains positive toughness");
        } finally { Config.ARMOR_DAMAGE_DESTROY_AT_MAX_LEVEL.set(destroy); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void damageProbabilityUsesNormalEnergyAndMaterial(GameTestHelper helper) {
        double base = probability(14, 16, 2.05, 1.95, 54);
        helper.assertTrue(base > 0 && base < Config.ARMOR_DAMAGE_MAX_PROBABILITY.get(), "Probability is bounded");
        helper.assertTrue(probability(28, 16, 2.05, 1.95, 54) > base
                && probability(14, 24, 2.05, 1.95, 54) > base
                && probability(14, 16, 4, 1.95, 54) > base, "Mass, normal speed and penetration increase damage chance");
        helper.assertTrue(probability(14, 16, 2.05, 3, 54) < base
                && probability(14, 16, 2.05, 1.95, 108) < base, "Stronger armor lowers damage chance");
        helper.assertTrue(probability(14, 0, 2.05, 1.95, 54) == 0, "Zero normal energy cannot add damage");
        helper.succeed();
    }

    private static double probability(double mass, double normalSpeed, double penetration, double hardness, double toughness) {
        return BlockDamageSavedData.damageProbability(new BlockDamageSavedData.ImpactDamage(
                mass, 32, normalSpeed, penetration, hardness, toughness, normalSpeed / 32));
    }

    private static final class SnapshotShot extends SolidShotProjectile {
        private double observedToughness;

        @SuppressWarnings("unchecked")
        SnapshotShot(Level level) {
            super((EntityType<SolidShotProjectile>) net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                    new net.minecraft.resources.ResourceLocation("createbigcannons", "shot")), level);
        }

        @Override protected Vec3 getForces(Vec3 position, Vec3 movement) { return Vec3.ZERO; }
        @Override protected BallisticPropertiesComponent getBallisticProperties() {
            return new BallisticPropertiesComponent(0, 0, false, 1000000, 10, 1, 0);
        }

        @Override protected ImpactResult calculateBlockPenetration(ProjectileContext context, BlockState state, BlockHitResult hit) {
            observedToughness = ArmorImpactContext.current(this).dynamicToughness();
            setProjectileMass(0);
            return new ImpactResult(ImpactResult.KinematicOutcome.STOP, false);
        }

        void collide() { clipAndDamage(); }
    }

    @GameTest(template = "empty")
    public static void collisionCommitsUsingPreImpactSnapshot(GameTestHelper helper) {
        BlockPos front = plate(helper);
        var level = helper.getLevel();
        BlockState state = level.getBlockState(front);
        SnapshotShot shot = new SnapshotShot(level);
        shot.setPos(front.getX() - .5, front.getY() + .5, front.getZ() + .5);
        shot.setDeltaMovement(1, 0, 0);
        shot.setProjectileMass(1000000);
        var data = BlockDamageSavedData.get(level);
        data.addDamageLevels(level, front, state, 1);
        double before = 54 * BlockDamageSavedData.toughnessMultiplier(1);
        try (ArmorImpactContext snapshot = ArmorImpactContext.open(shot, state, hit(front))) {
            near(helper, snapshot.effectiveBlockToughness(), before, "Snapshot retains damaged full block toughness");
            near(helper, snapshot.massCost(), before, "Mass debit uses full effective block toughness");
            shot.setProjectileMass(0);
            near(helper, snapshot.impactDamage().projectileMass(), 1000000, "Snapshot retains mass before CBC spends it");
        }
        shot.setProjectileMass(1000000);
        double maxProbability = Config.ARMOR_DAMAGE_MAX_PROBABILITY.get();
        double extra = Config.ARMOR_DAMAGE_EXTRA_LEVEL_PROBABILITY.get();
        try {
            Config.ARMOR_DAMAGE_MAX_PROBABILITY.set(1.0);
            Config.ARMOR_DAMAGE_EXTRA_LEVEL_PROBABILITY.set(0.0);
            shot.collide();
        } finally {
            Config.ARMOR_DAMAGE_MAX_PROBABILITY.set(maxProbability);
            Config.ARMOR_DAMAGE_EXTRA_LEVEL_PROBABILITY.set(extra);
        }
        near(helper, shot.observedToughness, before + 108, "CBC reads toughness before this hit's damage");
        helper.assertTrue(data.damageLevel(front, state) == 2 && data.damageLevel(front.east(), state) == 0,
                "Real collision adds one damage level after mass is spent, only to the struck block");
        helper.assertTrue(ArmorImpactContext.current(shot) == null, "Collision scope unwinds");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void damagePacketPreservesStateAndCrackStage(GameTestHelper helper) {
        BlockPos front = plate(helper);
        BlockState state = helper.getLevel().getBlockState(front);
        var status = new ArmorDamageState.Status(front, state, 2, 8, .7, -500);
        var packet = new ArmorNetwork.DamageSync(helper.getLevel().dimension().location(), new ChunkPos(front), true, List.of(status));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            helper.assertTrue(ArmorNetwork.DamageSync.decode(buffer).equals(packet), "Packet roundtrip preserves dimension, chunk and damage");
        } finally { buffer.release(); }
        helper.assertTrue(status.crackStage() == 2
                && new ArmorDamageState.Status(front, state, 0, 8, 1, -500).crackStage() == -1
                && new ArmorDamageState.Status(front, state, 7, 8, .1, -500).crackStage() == 8,
                "More damage produces deeper cracks and zero removes them");
        helper.succeed();
    }
}
