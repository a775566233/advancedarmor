package org.wgx.advancedarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.wgx.advancedarmor.compat.ShipSpace;
import org.wgx.advancedarmor.physics.VoxelRay;

import java.util.*;

/** Exact distance through contiguous armor voxels, measured in local block thickness. */
public final class ArmorPhysics {
    private ArmorPhysics() {}
    public record Profile(double totalToughness, double currentToughness, double effectiveHardness, double thickness, int blocks) {}

    public static Profile trace(Level level, BlockHitResult hit, Vec3 worldDirection) {
        ShipSpace space = ShipSpace.at(level, hit.getBlockPos());
        Vec3 direction = space.localDirection(worldDirection).normalize();
        Vec3 contact = hit.getLocation();
        // VS can return the contact in either coordinate space, depending on the clip overload.
        if (!inside(contact, hit.getBlockPos())) contact = space.localPosition(contact);
        // The collision result owns the hit voxel even when its contact lies exactly on
        // a shared edge. Math.floor would otherwise select the neighbouring air voxel,
        // giving this hit zero thickness and letting the projectile pass for free.
        if (!inside(contact, hit.getBlockPos()))
            return new Profile(Double.POSITIVE_INFINITY, 0, 1, 0, 0);
        Vec3 start = insideHitVoxel(contact.add(direction.scale(1.0e-7)), hit.getBlockPos());
        double[] result = new double[3];
        int[] blocks = {0};
        List<Double> allHardness = new ArrayList<>();
        VoxelRay.trace(start.x, start.y, start.z, direction.x, direction.y, direction.z,
                Config.ARMOR_DISTANCE.get(), Config.ARMOR_DISTANCE.get() * 4 + 16,
                (x, y, z, enter, exit) -> {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.isLoaded(pos)) return false;
                    ArmorData.Values armor = ArmorData.get(level.getBlockState(pos).getBlock());
                    if (armor == null) return false;
                    if (allHardness.size() <= Config.ARMOR_DISTANCE.get()) {
                        if (allHardness.isEmpty() || !allHardness.get(0).equals(Double.POSITIVE_INFINITY)) {
                            allHardness.add(armor.hardness());
                        }
                    } else {
                        allHardness.clear();
                        allHardness.add(Double.POSITIVE_INFINITY);
                    }
                    double thickness = exit - enter;
                    double effectiveToughness = ArmorDamageState.effectiveToughness(level, pos,
                            level.getBlockState(pos), armor.toughness());
                    result[0] += effectiveToughness * thickness;
                    if (pos.equals(hit.getBlockPos())) result[1] += effectiveToughness * thickness;
                    result[2] += thickness;
                    blocks[0]++;
                    return true;
                });
        double effectiveHardness = 0;
        if (allHardness.size() == 1) {
            effectiveHardness =  allHardness.get(0);
        } else if (!allHardness.isEmpty()) {
            double S = 0;
            for (int i = 1; i < allHardness.size(); i++) {
                S += (allHardness.get(i) / Config.FIXED_REFERENCE_HARDNESS.get()) * (i - 1);
            }
            effectiveHardness = allHardness.get(0) + Config.MAXIMUM_HARDNESS_INCREMENT.get() * (1 - Math.pow(Math.E, -1 * (Config.HARDNESS_INCREMENT_COEFFICIENT.get()) * S));
        }
        // The limit is a conservative stop: a plate continuing past it is never treated as thin.
        if (result[2] >= Config.ARMOR_DISTANCE.get() - 1.0e-5) result[0] = Double.POSITIVE_INFINITY;
        return new Profile(result[0], result[1], effectiveHardness, result[2], blocks[0]);
    }

    public static Vec3 worldContact(Level level, BlockHitResult hit) {
        return inside(hit.getLocation(), hit.getBlockPos())
                ? ShipSpace.at(level, hit.getBlockPos()).worldPosition(hit.getLocation()) : hit.getLocation();
    }

    private static boolean inside(Vec3 vector, BlockPos pos) {
        return vector.x >= pos.getX() - 1.0e-5 && vector.x <= pos.getX() + 1 + 1.0e-5
                && vector.y >= pos.getY() - 1.0e-5 && vector.y <= pos.getY() + 1 + 1.0e-5
                && vector.z >= pos.getZ() - 1.0e-5 && vector.z <= pos.getZ() + 1 + 1.0e-5;
    }

    private static Vec3 insideHitVoxel(Vec3 point, BlockPos pos) {
        final double inset = 1.0e-6;
        return new Vec3(
                Math.max(pos.getX() + inset, Math.min(pos.getX() + 1 - inset, point.x)),
                Math.max(pos.getY() + inset, Math.min(pos.getY() + 1 - inset, point.y)),
                Math.max(pos.getZ() + inset, Math.min(pos.getZ() + 1 - inset, point.z)));
    }
}
