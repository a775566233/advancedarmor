package org.wgx.advancedarmor.physics;

/** One ray's finite ledger: remaining + absorbed + dissipated always equals initial energy. */
public final class BlastEnergy {
    private final double initial;
    private double remaining;
    private double absorbed;
    private double dissipated;
    private double distance;
    public BlastEnergy(double initial) {
        if (!Double.isFinite(initial) || initial < 0) throw new IllegalArgumentException("Invalid blast energy");
        this.initial = initial;
        this.remaining = initial;
    }

    public void travelTo(double nextDistance, double airLoss) {
        if (nextDistance < distance) throw new IllegalArgumentException("Ray must travel forward");
        double spread = Math.pow((1 + distance) / (1 + nextDistance), 2);
        double after = Math.max(0, remaining * spread - airLoss * (nextDistance - distance));
        dissipated += remaining - after;
        remaining = after;
        distance = nextDistance;
    }

    /** Energy deposits in a voxel. True means there is enough left to fracture it and continue. */
    public boolean absorb(double cost) {
        if (!Double.isFinite(cost) || cost < 0) throw new IllegalArgumentException("Invalid absorption");
        double amount = Math.min(remaining, cost);
        remaining -= amount;
        absorbed += amount;
        return remaining > 1.0e-10;
    }

    public double remaining() { return remaining; }
    public double accounted() { return remaining + absorbed + dissipated; }
    public double initial() { return initial; }
}
