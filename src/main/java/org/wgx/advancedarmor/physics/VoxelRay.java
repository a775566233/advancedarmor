package org.wgx.advancedarmor.physics;

/** Amanatides-Woo traversal. Direction need not be unit length: t remains the caller's distance unit. */
public final class VoxelRay {
    private VoxelRay() {}
    @FunctionalInterface
    public interface Visitor {
        /** Return false to stop. Touching only an edge/corner never counts as material thickness. */
        boolean visit(int x, int y, int z, double enter, double exit);
    }

    public static void trace(double sx, double sy, double sz, double dx, double dy, double dz,
                             double limit, int maxCells, Visitor visitor) {
        if (limit <= 0 || maxCells <= 0 || !Double.isFinite(sx + sy + sz + dx + dy + dz)
                || dx * dx + dy * dy + dz * dz < 1.0e-20) return;
        int x = (int) Math.floor(sx), y = (int) Math.floor(sy), z = (int) Math.floor(sz);
        int stepX = Double.compare(dx, 0), stepY = Double.compare(dy, 0), stepZ = Double.compare(dz, 0);
        double deltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double deltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double deltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        double nextX = boundary(sx, x, dx), nextY = boundary(sy, y, dy), nextZ = boundary(sz, z, dz);
        double enter = 0;
        for (int cell = 0; cell < maxCells && enter < limit; cell++) {
            double exit = Math.min(limit, Math.min(nextX, Math.min(nextY, nextZ)));
            if (exit - enter > 1.0e-10 && !visitor.visit(x, y, z, enter, exit)) return;
            if (exit >= limit) return;
            // Advance all tied axes together so edge and corner hits do not create phantom armor.
            if (nextX <= exit + 1.0e-10) { x += stepX; nextX += deltaX; }
            if (nextY <= exit + 1.0e-10) { y += stepY; nextY += deltaY; }
            if (nextZ <= exit + 1.0e-10) { z += stepZ; nextZ += deltaZ; }
            enter = exit;
        }
    }

    private static double boundary(double start, int cell, double direction) {
        if (direction == 0) return Double.POSITIVE_INFINITY;
        return ((direction > 0 ? cell + 1.0 : cell) - start) / direction;
    }
}
