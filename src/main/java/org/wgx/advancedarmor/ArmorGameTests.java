package org.wgx.advancedarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraftforge.gametest.GameTestHolder;
import org.wgx.advancedarmor.compat.ShipSpace;
import org.wgx.advancedarmor.physics.BlastEnergy;
import org.wgx.advancedarmor.physics.VoxelRay;
import rbasamoyai.createbigcannons.config.CBCCfgMunitions;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.ap_shell.APShellProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.solid_shot.SolidShotProjectile;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;

import java.lang.reflect.Method;

/** Meaningful integration checks: reload data, physical plate thickness, VS bridge, ray accounting. */
@GameTestHolder(Advancedarmor.MODID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class ArmorGameTests {
    @GameTest(template = "empty")
    public static void layeredPlate(GameTestHelper helper) {
        helper.setBlock(1, 2, 1, Advancedarmor.block("kc_armor").get());
        helper.setBlock(2, 2, 1, Advancedarmor.block("sts_armor").get());
        BlockPos first = helper.absolutePos(new BlockPos(1, 2, 1));
        ArmorData.Values kc = ArmorData.get(helper.getLevel().getBlockState(first).getBlock());
        helper.assertTrue(kc != null && kc.toughness() == 54 && kc.hardness() == 1.95,
                "Built-in armor data must reload with exact table values");
        helper.assertTrue(Math.abs(helper.getLevel().getBlockState(first).getDestroySpeed(helper.getLevel(), first) - 1.95) < .001
                        && Math.abs(helper.getLevel().getBlockState(first).getBlock().getExplosionResistance() - 41) < .001,
                "Reloaded hardness and blast resistance must reach Minecraft's block methods");
        helper.assertTrue(ShipSpace.at(helper.getLevel(), first) == ShipSpace.WORLD,
                "VS bridge must identify stationary world blocks");
        Vec3 contact = new Vec3(first.getX(), first.getY() + .5, first.getZ() + .5);
        BlockHitResult hit = new BlockHitResult(contact, Direction.WEST, first, false);
        ArmorPhysics.Profile straight = ArmorPhysics.trace(helper.getLevel(), hit, new Vec3(1, 0, 0));
        ArmorPhysics.Profile angled = ArmorPhysics.trace(helper.getLevel(), hit, new Vec3(1, .5, 0));
        helper.assertTrue(straight.blocks() == 2 && Math.abs(straight.totalToughness() - 92) < .01,
                "Two armor voxels must sum their toughness exactly");
        helper.assertTrue(angled.totalToughness() > kc.toughness(),
                "Inclined incidence must increase path thickness");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedEdgeDoesNotBypassLayeredArmor(GameTestHelper helper) throws Exception {
        for (int x = 1; x <= 6; x++)
            helper.setBlock(x, 2, 1, Advancedarmor.block("kc_armor").get());
        BlockPos front = helper.absolutePos(new BlockPos(1, 2, 1));
        // A hit on the exact Z edge belongs to the reported block. The neighbouring
        // Z voxel is air, so an unanchored DDA would report zero armor thickness.
        Vec3 edge = new Vec3(front.getX(), front.getY() + .5, front.getZ() + 1);
        BlockHitResult hit = new BlockHitResult(edge, Direction.WEST, front, false);
        ArmorPhysics.Profile profile = ArmorPhysics.trace(helper.getLevel(), hit, new Vec3(1, 0, 0));
        helper.assertTrue(profile.blocks() == 6 && Math.abs(profile.totalToughness() - 324) < .001,
                "An exact shared-edge hit must count all six blocks behind the hit voxel");

        @SuppressWarnings("unchecked")
        net.minecraft.world.entity.EntityType<SolidShotProjectile> type =
                (net.minecraft.world.entity.EntityType<SolidShotProjectile>)
                        net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                                new net.minecraft.resources.ResourceLocation("createbigcannons", "shot"));
        SolidShotProjectile shot = type.create(helper.getLevel());
        helper.assertTrue(shot != null, "CBC must create the edge-hit test projectile");
        shot.setPos(front.getX() - 1, edge.y, edge.z);
        shot.setDeltaMovement(1, 0, 0);
        shot.setProjectileMass(40);
        Method impact = AbstractBigCannonProjectile.class.getDeclaredMethod("calculateBlockPenetration",
                ProjectileContext.class, net.minecraft.world.level.block.state.BlockState.class, BlockHitResult.class);
        impact.setAccessible(true);
        impact.invoke(shot, new ProjectileContext(shot, CBCCfgMunitions.GriefState.ALL_DAMAGE),
                helper.getLevel().getBlockState(front), hit);
        helper.assertTrue(!helper.getLevel().getBlockState(front).isAir(),
                "A shell below the first layer's cost must stop at its shared edge");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void projectileStopsBeforeFourLayerPlate(GameTestHelper helper) throws Exception {
        for (int x = 1; x <= 4; x++)
            helper.setBlock(x, 2, 1, Advancedarmor.block("knc_armor").get());
        BlockPos front = helper.absolutePos(new BlockPos(1, 2, 1));
        @SuppressWarnings("unchecked")
        net.minecraft.world.entity.EntityType<SolidShotProjectile> type =
                (net.minecraft.world.entity.EntityType<SolidShotProjectile>)
                        net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                                new net.minecraft.resources.ResourceLocation("createbigcannons", "shot"));
        SolidShotProjectile shot = type.create(helper.getLevel());
        helper.assertTrue(shot != null, "CBC must create the layer test projectile");
        shot.setPos(front.getX() - 1, front.getY() + .5, front.getZ() + .5);
        shot.setDeltaMovement(1, 0, 0);
        shot.setProjectileMass(280);
        Method impact = AbstractBigCannonProjectile.class.getDeclaredMethod("calculateBlockPenetration",
                ProjectileContext.class, net.minecraft.world.level.block.state.BlockState.class, BlockHitResult.class);
        impact.setAccessible(true);
        ProjectileContext context = new ProjectileContext(shot, CBCCfgMunitions.GriefState.ALL_DAMAGE);
        for (int x = 1; x <= 4; x++) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, 2, 1));
            impact.invoke(shot, context, helper.getLevel().getBlockState(pos),
                    new BlockHitResult(new Vec3(pos.getX(), pos.getY() + .5, pos.getZ() + .5),
                            Direction.WEST, pos, false));
        }
        for (int x = 1; x <= 4; x++) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, 2, 1));
            helper.assertTrue(!helper.getLevel().getBlockState(pos).isAir(),
                    "A projectile below the four-layer dynamic cost must stop before the plate (layer "
                            + x + ", remaining mass " + shot.getProjectileMass() + ")");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dynamicToughnessBlocksSixteenLayerPlate(GameTestHelper helper) throws Exception {
        for (int x = 1; x <= 16; x++)
            helper.setBlock(x, 2, 1, Advancedarmor.block("kc_armor").get());
        BlockPos front = helper.absolutePos(new BlockPos(1, 2, 1));
        ArmorPhysics.Profile profile = ArmorPhysics.trace(helper.getLevel(),
                new BlockHitResult(new Vec3(front.getX(), front.getY() + .5, front.getZ() + .5),
                        Direction.WEST, front, false), new Vec3(1, 0, 0));
        helper.assertTrue(profile.blocks() == 16 && Math.abs(profile.totalToughness() - 864) < .001,
                "Sixteen KC layers must expose 16 * 54 effective toughness");

        @SuppressWarnings("unchecked")
        net.minecraft.world.entity.EntityType<APShellProjectile> type =
                (net.minecraft.world.entity.EntityType<APShellProjectile>)
                        net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                                new net.minecraft.resources.ResourceLocation("createbigcannons", "ap_shell"));
        APShellProjectile shell = type.create(helper.getLevel());
        helper.assertTrue(shell != null, "CBC must create the dynamic-thickness test shell");
        shell.setPos(front.getX() - .5, front.getY() + .5, front.getZ() + .5);
        shell.setDeltaMovement(8, 0, 0);
        shell.setProjectileMass(7.5f);
        Method impact = AbstractBigCannonProjectile.class.getDeclaredMethod("calculateBlockPenetration",
                ProjectileContext.class, net.minecraft.world.level.block.state.BlockState.class, BlockHitResult.class);
        impact.setAccessible(true);
        impact.invoke(shell, new ProjectileContext(shell, CBCCfgMunitions.GriefState.ALL_DAMAGE),
                helper.getLevel().getBlockState(front),
                new BlockHitResult(new Vec3(front.getX(), front.getY() + .5, front.getZ() + .5),
                        Direction.WEST, front, false));
        helper.assertTrue(!helper.getLevel().getBlockState(front).isAir()
                        && shell.getProjectileMass() == 0,
                "A 7.5-mass shell must stop before a 16-layer KC plate");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void angledPlateUsesSecantThickness(GameTestHelper helper) {
        // A wide two-block slab keeps every DDA cell on the ray inside armor.
        // The slab's normal thickness is two blocks, so the oblique path is
        // exactly 2/cos(angle).
        for (int x = 1; x <= 2; x++)
            for (int y = 2; y <= 4; y++)
                helper.setBlock(x, y, 1, Advancedarmor.block("kc_armor").get());
        BlockPos first = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockHitResult hit = new BlockHitResult(new Vec3(first.getX(), first.getY() + .5, first.getZ() + .5),
                Direction.WEST, first, false);
        ArmorPhysics.Profile profile = ArmorPhysics.trace(helper.getLevel(), hit, new Vec3(1, .5, 0));
        double sec = Math.sqrt(1.25);
        helper.assertTrue(profile.blocks() > 0
                        && Math.abs(profile.totalToughness() - 2 * 54 * sec) < .001,
                "Angled armor must multiply the full normal thickness by the ray secant");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void obliqueImpactUsesCbcFullBlockMassDebit(GameTestHelper helper) throws Exception {
        BlockPos front = helper.absolutePos(new BlockPos(1, 2, 1));
        double angle = Math.toRadians(40.81);
        double cosine = Math.cos(angle);
        double expectedLoss = 54 / (12 * cosine);
        helper.assertTrue(expectedLoss > 5.9 && expectedLoss < 6.0,
                "The screenshot's 40.81-degree CBC reference should lose about 5.96 mass");

        @SuppressWarnings("unchecked")
        net.minecraft.world.entity.EntityType<APShellProjectile> type =
                (net.minecraft.world.entity.EntityType<APShellProjectile>)
                        net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                                new net.minecraft.resources.ResourceLocation("createbigcannons", "ap_shell"));
        Method impact = AbstractBigCannonProjectile.class.getDeclaredMethod("calculateBlockPenetration",
                ProjectileContext.class, net.minecraft.world.level.block.state.BlockState.class, BlockHitResult.class);
        impact.setAccessible(true);
        double[] losses = new double[2];
        for (int i = 0; i < losses.length; i++) {
            helper.setBlock(1, 2, 1, Advancedarmor.block("kc_armor").get());
            // The second contact leaves the hit voxel almost immediately. CBC
            // still charges the complete block on both impacts.
            double contactY = front.getY() + (i == 0 ? .5 : .95);
            APShellProjectile shell = type.create(helper.getLevel());
            helper.assertTrue(shell != null, "CBC must create the oblique mass-debit shell");
            shell.setPos(front.getX() - .5, contactY, front.getZ() + .5);
            shell.setDeltaMovement(12 * cosine, 12 * Math.sin(angle), 0);
            shell.setProjectileMass(14);
            BlockHitResult hit = new BlockHitResult(
                    new Vec3(front.getX(), contactY, front.getZ() + .5), Direction.WEST, front, false);
            impact.invoke(shell, new ProjectileContext(shell, CBCCfgMunitions.GriefState.ALL_DAMAGE),
                    helper.getLevel().getBlockState(front), hit);
            losses[i] = 14 - shell.getProjectileMass();
            helper.assertTrue(helper.getLevel().getBlockState(front).isAir()
                            && Math.abs(losses[i] - expectedLoss) < .15,
                    "An oblique KC hit must debit CBC's full 54/(12*cos) mass, even near a voxel edge");
        }
        helper.assertTrue(Math.abs(losses[0] - losses[1]) < .02,
                "Moving the same oblique hit toward the block edge must not reduce mass loss");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void grazingShellLosesNormalMomentum(GameTestHelper helper) throws Exception {
        // The shot crosses one block of normal depth but nearly four blocks of
        // KC armor along its path. This distinguishes secant thickness from the
        // separate CBC cosine term in the projectile's available momentum.
        for (int y = 2; y <= 7; y++)
            helper.setBlock(1, y, 1, Advancedarmor.block("kc_armor").get());
        BlockPos front = helper.absolutePos(new BlockPos(1, 2, 1));
        double cosine = .25;
        double sine = Math.sqrt(1 - cosine * cosine);
        BlockHitResult hit = new BlockHitResult(
                new Vec3(front.getX(), front.getY() + .5, front.getZ() + .5),
                Direction.WEST, front, false);
        ArmorPhysics.Profile profile = ArmorPhysics.trace(helper.getLevel(), hit, new Vec3(cosine, sine, 0));
        double bonus = 1 + (12 - 1) * .1;
        double budget = ArmorImpactPhysics.penetrationBudget(14, 12, cosine, bonus);
        helper.assertTrue(Math.abs(profile.totalToughness() - 54 / cosine) < .001
                        && budget < profile.totalToughness()
                        && 14 * 12 * bonus > profile.totalToughness(),
                "Grazing penetration must use both the full secant thickness and CBC's normal momentum");

        @SuppressWarnings("unchecked")
        net.minecraft.world.entity.EntityType<APShellProjectile> type =
                (net.minecraft.world.entity.EntityType<APShellProjectile>)
                        net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                                new net.minecraft.resources.ResourceLocation("createbigcannons", "ap_shell"));
        APShellProjectile shell = type.create(helper.getLevel());
        helper.assertTrue(shell != null, "CBC must create the grazing test shell");
        shell.setPos(front.getX() - .5, front.getY() + .5, front.getZ() + .5);
        shell.setDeltaMovement(12 * cosine, 12 * sine, 0);
        shell.setProjectileMass(14);
        Method impact = AbstractBigCannonProjectile.class.getDeclaredMethod("calculateBlockPenetration",
                ProjectileContext.class, net.minecraft.world.level.block.state.BlockState.class, BlockHitResult.class);
        impact.setAccessible(true);
        impact.invoke(shell, new ProjectileContext(shell, CBCCfgMunitions.GriefState.ALL_DAMAGE),
                helper.getLevel().getBlockState(front), hit);
        helper.assertTrue(!helper.getLevel().getBlockState(front).isAir(),
                "A grazing AP shell below the normal-momentum budget must leave KC armor intact");

        double armorBounce = ArmorImpactPhysics.bounceChance(.33, .2, .7, 1.95, 2.05);
        double cbcWhiteBounce = .33 * (1 - .2 / .7) * (1 - (1.7 - 2.05));
        helper.assertTrue(armorBounce > cbcWhiteBounce,
                "KC armor must deflect this grazing shell more often than CBC's softer 1.7-hardness block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void apShellLosesMassAcrossFourLayerPlate(GameTestHelper helper) throws Exception {
        for (int x = 1; x <= 4; x++)
            helper.setBlock(x, 2, 1, Advancedarmor.block("knc_armor").get());
        BlockPos front = helper.absolutePos(new BlockPos(1, 2, 1));
        @SuppressWarnings("unchecked")
        net.minecraft.world.entity.EntityType<APShellProjectile> type =
                (net.minecraft.world.entity.EntityType<APShellProjectile>)
                        net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                                new net.minecraft.resources.ResourceLocation("createbigcannons", "ap_shell"));
        APShellProjectile shell = type.create(helper.getLevel());
        helper.assertTrue(shell != null, "CBC must create the AP shell projectile");
        shell.setPos(front.getX() - .5, front.getY() + .5, front.getZ() + .5);
        // 160 blocks/second equals 8 blocks/tick in CBC's motion units.
        shell.setDeltaMovement(8, 0, 0);
        shell.setProjectileMass(14);
        Method collision = AbstractCannonProjectile.class.getDeclaredMethod("clipAndDamage");
        collision.setAccessible(true);
        collision.invoke(shell);
        helper.assertTrue(helper.getLevel().getBlockState(front).isAir(),
                "The AP shell must penetrate the first KNC block");
        helper.assertTrue(!helper.getLevel().getBlockState(front.east()).isAir()
                        && !helper.getLevel().getBlockState(front.east(2)).isAir()
                        && !helper.getLevel().getBlockState(front.east(3)).isAir(),
                "After the first dynamic plate gate, the AP shell must stop before KNC layer two");
        helper.assertTrue(shell.getProjectileMass() == 0,
                "CBC must exhaust projectile mass when the second layer stops the shell");

        // Independently check the numeric debit for one full block. CBC's speed
        // bonus may help the penetration check, but must not divide this debit.
        helper.setBlock(1, 2, 1, Advancedarmor.block("knc_armor").get());
        APShellProjectile singleHit = type.create(helper.getLevel());
        helper.assertTrue(singleHit != null, "CBC must create the mass accounting shell");
        singleHit.setPos(front.getX() - .5, front.getY() + .5, front.getZ() + .5);
        singleHit.setDeltaMovement(8, 0, 0);
        singleHit.setProjectileMass(14);
        Method impact = AbstractBigCannonProjectile.class.getDeclaredMethod("calculateBlockPenetration",
                ProjectileContext.class, net.minecraft.world.level.block.state.BlockState.class, BlockHitResult.class);
        impact.setAccessible(true);
        impact.invoke(singleHit, new ProjectileContext(singleHit, CBCCfgMunitions.GriefState.ALL_DAMAGE),
                helper.getLevel().getBlockState(front),
                new BlockHitResult(new Vec3(front.getX(), front.getY() + .5, front.getZ() + .5),
                        Direction.WEST, front, false));
        helper.assertTrue(singleHit.getProjectileMass() > 8 && singleHit.getProjectileMass() < 8.5,
                "One KNC layer must debit approximately 46/8 mass, without the speed bonus divisor");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void energyIsConserved(GameTestHelper helper) {
        BlastEnergy energy = new BlastEnergy(100);
        energy.travelTo(1, 2);
        helper.assertTrue(energy.absorb(8), "A partially absorbed ray must continue");
        energy.travelTo(2, 2);
        helper.assertTrue(Math.abs(energy.accounted() - energy.initial()) < 1.0e-8,
                "Travel and absorption must conserve the finite energy ledger");
        helper.assertTrue(!energy.absorb(1000) && energy.remaining() == 0,
                "A fully absorbed ray must stop");
        int[] count = {0};
        double[] thickness = {0};
        VoxelRay.trace(.5, .5, .5, 1, 1, 0, 3, 20, (x, y, z, enter, exit) -> {
            count[0]++;
            thickness[0] += exit - enter;
            return true;
        });
        helper.assertTrue(count[0] == 4 && Math.abs(thickness[0] - 3) < 1.0e-8,
                "DDA must cross tied corners once and account for the entire ray");
        org.joml.Matrix4d rotated = new org.joml.Matrix4d().translate(100, 20, -40).rotateY(Math.PI / 2);
        ShipSpace shipSpace = new ShipSpace(rotated, new org.joml.Matrix4d(rotated).invert());
        Vec3 local = shipSpace.localDirection(shipSpace.worldDirection(new Vec3(1, .5, 0)));
        helper.assertTrue(local.distanceTo(new Vec3(1, .5, 0)) < 1.0e-8,
                "Rotated VS ship direction must round-trip between local and world coordinates");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cbcProjectileAndBlast(GameTestHelper helper) throws Exception {
        helper.setBlock(1, 2, 1, Advancedarmor.block("wrought_iron").get());
        helper.setBlock(2, 2, 1, Advancedarmor.block("kc_armor").get());
        BlockPos front = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos back = helper.absolutePos(new BlockPos(2, 2, 1));
        @SuppressWarnings("unchecked")
        net.minecraft.world.entity.EntityType<SolidShotProjectile> type =
                (net.minecraft.world.entity.EntityType<SolidShotProjectile>)
                        net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                                new net.minecraft.resources.ResourceLocation("createbigcannons", "shot"));
        SolidShotProjectile shot = type.create(helper.getLevel());
        helper.assertTrue(shot != null, "CBC must create a solid-shot projectile");
        shot.setPos(front.getX() - 1, front.getY() + .5, front.getZ() + .5);
        shot.setDeltaMovement(1, 0, 0);
        shot.setProjectileMass(200);
        Method impact = AbstractBigCannonProjectile.class.getDeclaredMethod("calculateBlockPenetration",
                ProjectileContext.class, net.minecraft.world.level.block.state.BlockState.class, BlockHitResult.class);
        impact.setAccessible(true);
        impact.invoke(shot, new ProjectileContext(shot, CBCCfgMunitions.GriefState.ALL_DAMAGE),
                helper.getLevel().getBlockState(front),
                new BlockHitResult(new Vec3(front.getX(), front.getY() + .5, front.getZ() + .5), Direction.WEST, front, false));
        helper.assertTrue(helper.getLevel().getBlockState(front).isAir() && !helper.getLevel().getBlockState(back).isAir(),
                "CBC penetration must remove the hit voxel and leave the back armor for the next collision");
        helper.assertTrue(shot.getProjectileMass() < 200, "CBC projectile must spend mass on the current plate voxel");

        helper.setBlock(1, 2, 1, Advancedarmor.block("wrought_iron").get());
        SolidShotProjectile weakShot = type.create(helper.getLevel());
        helper.assertTrue(weakShot != null, "CBC must create the comparison projectile");
        weakShot.setPos(front.getX() - 1, front.getY() + .5, front.getZ() + .5);
        weakShot.setDeltaMovement(1, 0, 0);
        weakShot.setProjectileMass(1);
        impact.invoke(weakShot, new ProjectileContext(weakShot, CBCCfgMunitions.GriefState.ALL_DAMAGE),
                helper.getLevel().getBlockState(front),
                new BlockHitResult(new Vec3(front.getX(), front.getY() + .5, front.getZ() + .5), Direction.WEST, front, false));
        helper.assertTrue(!helper.getLevel().getBlockState(front).isAir(),
                "A weak CBC shot must be stopped by the two-block armor layer");

        ShellExplosion blast = new ShellExplosion(helper.getLevel(), null, null,
                back.getX() - 1, back.getY() + .5, back.getZ() + .5,
                4, false, net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
        blast.explode();
        helper.assertTrue(blast.getToBlow().isEmpty(),
                "Armor rays must not invent destruction when CBC selected no blocks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armorBlastOnlyFiltersCbcDestruction(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos armorPos = origin.east();
        BlockPos behind = origin.east(3);
        BlockPos side = origin.south(3);
        BlockPos resistant = origin.west(3);
        helper.setBlock(2, 2, 1, Advancedarmor.block("kc_armor").get());
        helper.setBlock(4, 2, 1, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(1, 2, 4, net.minecraft.world.level.block.Blocks.STONE);
        ShellExplosion blast = new ShellExplosion(helper.getLevel(), null, null,
                origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5,
                4, false, net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
        java.util.List<BlockPos> cbcCandidates = java.util.List.of(armorPos, behind, side);
        java.util.List<BlockPos> withoutExtra = BlastPropagation.select(helper.getLevel(), blast, 4,
                java.util.List.of(resistant, side));
        helper.assertTrue(withoutExtra.equals(java.util.List.of(resistant, side)),
                "Ordinary blocks must keep exactly CBC's result without armor on their rays");

        java.util.Map<net.minecraft.world.level.block.Block, ArmorData.Values> previous = ArmorData.snapshot();
        try {
            java.util.Map<net.minecraft.world.level.block.Block, ArmorData.Values> modified = new java.util.HashMap<>(previous);
            modified.put(Advancedarmor.block("kc_armor").get(), new ArmorData.Values(1.95, 54, 10000));
            ArmorData.replace(modified);
            java.util.List<BlockPos> filtered = BlastPropagation.select(helper.getLevel(), blast, 4, cbcCandidates);
            helper.assertTrue(cbcCandidates.containsAll(filtered),
                    "Armor filtering must never add a block CBC would not destroy");
            helper.assertTrue(!filtered.contains(behind) && filtered.contains(side),
                    "Finite armor absorption must shield a block behind it without affecting the side");
        } finally {
            ArmorData.replace(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dataPackUpdatesOtherBlocks(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.setBlock(1, 2, 1, net.minecraft.world.level.block.Blocks.OBSIDIAN);
        java.util.Map<net.minecraft.world.level.block.Block, ArmorData.Values> previous = ArmorData.snapshot();
        try {
            java.util.Map<net.minecraft.world.level.block.Block, ArmorData.Values> modified = new java.util.HashMap<>(previous);
            modified.put(net.minecraft.world.level.block.Blocks.OBSIDIAN, new ArmorData.Values(7, 65, 51));
            ArmorData.replace(modified);
            helper.assertTrue(Math.abs(helper.getLevel().getBlockState(pos).getDestroySpeed(helper.getLevel(), pos) - 7) < .001,
                    "A data pack can change mining hardness of an existing foreign block");
            helper.assertTrue(Math.abs(net.minecraft.world.level.block.Blocks.OBSIDIAN.getExplosionResistance() - 51) < .001,
                    "A data pack can change blast resistance of an existing foreign block");
            helper.assertTrue(Math.abs(rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler
                    .getProperties(helper.getLevel().getBlockState(pos))
                    .toughness(helper.getLevel(), helper.getLevel().getBlockState(pos), pos, true) - 65) < .001,
                    "A data pack can change CBC toughness of an existing foreign block");
        } finally {
            ArmorData.replace(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeArmorTab(GameTestHelper helper) {
        net.minecraft.resources.ResourceLocation tabId = new net.minecraft.resources.ResourceLocation(Advancedarmor.MODID, "armor");
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(tabId),
                "The Advanced Armor creative tab must be registered");
        net.minecraft.world.item.CreativeModeTab tab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(tabId);
        helper.assertTrue(tab.getIconItem().is(Advancedarmor.block("kc_armor").get().asItem()),
                "KC armor must be the creative tab icon");
        for (String name : new String[] {"wrought_iron", "homogeneous_carbon_steel", "iron_steel_composite",
                "nickel_steel", "harvey_nickel_steel", "kc_armor", "knc_armor", "sts_armor",
                "ducol_steel", "british_plastic_protection"}) {
            helper.assertTrue(net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(
                    new net.minecraft.resources.ResourceLocation(Advancedarmor.MODID, name)),
                    "Missing creative inventory block item: " + name);
        }
        helper.succeed();
    }
}
