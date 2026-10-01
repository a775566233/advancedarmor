package org.wgx.advancedarmor;

/** Angle and ricochet terms for data-pack armor in CBC projectile impacts. */
public final class ArmorImpactPhysics {
    private ArmorImpactPhysics() {}

    /** CBC's impact budget includes the velocity component normal to the face. */
    public static double penetrationBudget(double mass, double speed, double cosine, double velocityBonus) {
        return mass * speed * Math.max(0, cosine) * velocityBonus;
    }

    /**
     * CBC charges one complete hit block per penetration. The normal component
     * of velocity is the divisor; a short DDA segment near an edge must not
     * reduce this debit. Dynamic path length belongs to the penetration gate.
     */
    public static double massCost(double blockToughness, double hardnessDifference,
                                  double speed, double cosine) {
        double normalSpeed = speed * Math.max(0, cosine);
        if (normalSpeed < 1.0e-4) return Double.POSITIVE_INFINITY;
        return blockToughness * (1 + Math.max(0, hardnessDifference)) / normalSpeed;
    }

    /**
     * Armor hardness must increase, not decrease, its chance of deflecting a
     * grazing hit. The shell's penetration rating scales this contribution.
     */
    public static double bounceChance(double baseChance, double cosine, double deflection,
                                      double hardness, double penetration) {
        if (deflection <= 0.01 || cosine > deflection) return 0;
        double hardnessFactor = 1 + hardness / Math.max(1, penetration);
        return Math.min(1, Math.max(0, baseChance * (1 - cosine / deflection) * hardnessFactor));
    }
}
