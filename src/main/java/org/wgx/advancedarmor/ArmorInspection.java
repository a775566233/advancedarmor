package org.wgx.advancedarmor;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler;
import rbasamoyai.createbigcannons.utils.CBCUtils;

/** Read-only inspection uses exactly the same directional path as projectile impacts. */
public final class ArmorInspection {
    private ArmorInspection() {}

    public record Result(double toughness, double hardness, int blocks, double angleDegrees,
                         int damageLevel, int maxDamageLevel, double blockToughness, double toughnessLossPercent) {}

    public static Result inspect(Level level, BlockHitResult hit, Vec3 viewDirection) {
        if (hit.getType() != HitResult.Type.BLOCK || !level.isLoaded(hit.getBlockPos())
                || viewDirection.lengthSqr() < 1.0e-12) return null;
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (state.isAir()) return null;

        Vec3 direction = viewDirection.normalize();
        // CBC supplies a world-space normal, including our VS transform hook.
        Vec3 normal = CBCUtils.getSurfaceNormalVector(level, hit).normalize();
        double cosine = Math.max(0, Math.min(1, -direction.dot(normal)));
        double angle = Math.toDegrees(Math.acos(cosine));
        ArmorData.Values armor = ArmorData.get(state.getBlock());
        if (armor != null) {
            ArmorPhysics.Profile profile = ArmorPhysics.trace(level, hit, direction);
            // Path length already accounts for sec(angle); multiplying again
            // would make inspection disagree with the actual penetration gate.
            double toughness = profile.blocks() == 0 ? Double.POSITIVE_INFINITY : profile.totalToughness();
            double hardness = profile.blocks() == 0 ? Double.POSITIVE_INFINITY : profile.effectiveHardness();
            ArmorDamageState.Status damage = ArmorDamageState.get(level, hit.getBlockPos(), state);
            return new Result(toughness, hardness, profile.blocks(), angle, damage.level(), damage.maxLevel(),
                    armor.toughness() * damage.multiplier(), 100 * (1 - damage.multiplier()));
        }

        // Ordinary blocks keep CBC's single-block attributes and penetration model.
        var provider = BlockArmorPropertiesHandler.getProperties(state);
        return new Result(provider.toughness(level, state, hit.getBlockPos(), true),
                provider.hardness(level, state, hit.getBlockPos(), true), 1, angle, 0, 0,
                provider.toughness(level, state, hit.getBlockPos(), true), 0);
    }
}
