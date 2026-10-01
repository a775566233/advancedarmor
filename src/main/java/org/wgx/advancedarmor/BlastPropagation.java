package org.wgx.advancedarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.wgx.advancedarmor.compat.ShipSpace;
import org.wgx.advancedarmor.physics.BlastEnergy;
import org.wgx.advancedarmor.physics.VoxelRay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Armor can only remove blocks from CBC's already-computed explosion result. */
public final class BlastPropagation {
    private BlastPropagation() {}
    private record Cell(double enter, double exit, double resistance) {}
    private record Ray(Vec3 direction, double stopAt) {}

    /**
     * Each fixed angular sector owns one finite energy ledger. Only data-pack armor
     * absorbs energy here; CBC keeps authority over all ordinary block destruction.
     * A stopped sector can shield blocks behind its armor, never add destruction.
     */
    public static List<BlockPos> select(Level level, Explosion explosion, float radius, List<BlockPos> cbcBlocks) {
        if (!explosion.interactsWithBlocks() || radius <= 0 || !Float.isFinite(radius)) return List.of();
        if (cbcBlocks.isEmpty()) return List.of();
        Vec3 rawOrigin = explosion.getPosition();
        Vec3 origin = ShipSpace.at(level, BlockPos.containing(rawOrigin)).worldPosition(rawOrigin);
        double range = Math.min(Config.BLAST_DISTANCE.get(), radius * 2.0);
        int count = Config.BLAST_RAYS.get();
        double energyRadius = Math.min(radius, Config.BLAST_DISTANCE.get() / 2.0);
        double energyPerRay = Config.BLAST_ENERGY.get() * energyRadius * energyRadius * energyRadius / count;
        List<ShipSpace> spaces = ShipSpace.nearby(level, origin, range);
        List<Ray> rays = new ArrayList<>(count);
        for (int ray = 0; ray < count; ray++) {
            double y = 1 - 2 * (ray + 0.5) / count;
            double radial = Math.sqrt(1 - y * y);
            double angle = ray * Math.PI * (3 - Math.sqrt(5));
            Vec3 direction = new Vec3(Math.cos(angle) * radial, y, Math.sin(angle) * radial);
            List<Cell> armorCells = new ArrayList<>();
            for (ShipSpace space : spaces) {
                Vec3 localOrigin = space.localPosition(origin);
                Vec3 localDirection = space.localDirection(direction);
                VoxelRay.trace(localOrigin.x, localOrigin.y, localOrigin.z,
                        localDirection.x, localDirection.y, localDirection.z, range, 2048,
                        (x, cy, z, enter, exit) -> {
                            BlockPos pos = new BlockPos(x, cy, z);
                            if (!level.isLoaded(pos)) return false;
                            ArmorData.Values armor = ArmorData.get(level.getBlockState(pos).getBlock());
                            if (armor != null) armorCells.add(new Cell(enter, exit, armor.explosionResistance()));
                            return true;
                        });
            }
            armorCells.sort(Comparator.comparingDouble(Cell::enter));
            BlastEnergy energy = new BlastEnergy(energyPerRay);
            double stopAt = Double.POSITIVE_INFINITY;
            double traveled = 0;
            for (Cell cell : armorCells) {
                if (cell.exit() <= traveled + 1.0e-10) continue;
                double enter = Math.max(traveled, cell.enter());
                energy.travelTo(enter, Config.AIR_LOSS.get() / count);
                if (energy.remaining() <= 1.0e-10) { stopAt = enter; break; }
                double cost = cell.resistance() * (cell.exit() - enter)
                        * Config.BLAST_ABSORPTION.get() / count;
                if (!energy.absorb(cost)) { stopAt = enter; break; }
                traveled = cell.exit();
                energy.travelTo(traveled, Config.AIR_LOSS.get() / count);
                if (energy.remaining() <= 1.0e-10) { stopAt = traveled; break; }
            }
            rays.add(new Ray(direction, stopAt));
        }

        // Preserve CBC's order. A block is removed only when armor in its angular
        // sector spent that ray's finite budget before reaching the block.
        List<BlockPos> kept = new ArrayList<>(cbcBlocks.size());
        for (BlockPos pos : cbcBlocks) {
            ShipSpace space = ShipSpace.at(level, pos);
            Vec3 center = space.worldPosition(Vec3.atCenterOf(pos)).subtract(origin);
            double distance = center.length();
            if (distance < 1.0e-10) { kept.add(pos); continue; }
            Vec3 toward = center.scale(1 / distance);
            Ray nearest = null;
            double bestDot = -Double.MAX_VALUE;
            for (Ray ray : rays) {
                double dot = toward.dot(ray.direction());
                if (dot > bestDot) { bestDot = dot; nearest = ray; }
            }
            if (nearest == null || distance <= nearest.stopAt() + 1.0e-8) kept.add(pos);
        }
        return kept;
    }
}
