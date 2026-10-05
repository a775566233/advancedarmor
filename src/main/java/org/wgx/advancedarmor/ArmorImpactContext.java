package org.wgx.advancedarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.wgx.advancedarmor.mixin.CannonProjectileAccessor;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.utils.CBCUtils;

/** A pre-destruction armor snapshot scoped to one virtual projectile impact call. */
public final class ArmorImpactContext implements AutoCloseable {
    private static final ThreadLocal<ArmorImpactContext> ACTIVE = new ThreadLocal<>();

    private final ArmorImpactContext previous;
    private final AbstractCannonProjectile projectile;
    private final Level level;
    private final BlockPos pos;
    private final BlockState state;
    private final ArmorData.Values armor;
    private final ArmorPhysics.Profile profile;
    private final BallisticPropertiesComponent shell;
    private final double speed;
    private final double cosine;
    private final double projectileMass;
    private final double effectiveBlockToughness;
    private boolean closed;

    private ArmorImpactContext(AbstractCannonProjectile projectile, BlockState state, BlockHitResult hit) {
        this.previous = ACTIVE.get();
        this.projectile = projectile;
        this.level = projectile.level();
        this.pos = hit.getBlockPos().immutable();
        this.state = state;
        this.armor = ArmorData.get(state.getBlock());
        this.projectileMass = projectile.getProjectileMass();
        this.effectiveBlockToughness = armor == null ? 0
                : ArmorDamageState.effectiveToughness(level, pos, state, armor.toughness());
        this.shell = ((CannonProjectileAccessor) projectile).advancedarmor$invokeBallisticProperties();
        if (armor != null) {
            Vec3 movement = projectile.getDeltaMovement();
            Vec3 velocity = movement.add(((CannonProjectileAccessor) projectile)
                    .advancedarmor$invokeForces(projectile.position(), movement));
            this.speed = velocity.length();
            this.cosine = Math.max(0, Math.min(1,
                    -velocity.normalize().dot(CBCUtils.getSurfaceNormalVector(level, hit).normalize())));
            this.profile = ArmorPhysics.trace(level, hit, velocity);
        } else {
            this.speed = 0;
            this.cosine = 0;
            this.profile = null;
        }
    }

    /** Also masks an outer impact when a nested projectile hits an ordinary block. */
    public static ArmorImpactContext open(AbstractCannonProjectile projectile, BlockState state, BlockHitResult hit) {
        ArmorImpactContext context = new ArmorImpactContext(projectile, state, hit);
        ACTIVE.set(context);
        return context;
    }

    public static ArmorImpactContext current(AbstractCannonProjectile projectile) {
        ArmorImpactContext context = ACTIVE.get();
        return context != null && context.projectile == projectile && context.armor != null ? context : null;
    }

    private static ArmorImpactContext matching(Level level, BlockState state, BlockPos pos) {
        ArmorImpactContext context = ACTIVE.get();
        return context != null && context.armor != null && context.level == level
                && context.pos.equals(pos) && context.state.equals(state) ? context : null;
    }

    public static double toughness(Level level, BlockState state, BlockPos pos, double fallback) {
        ArmorImpactContext context = matching(level, state, pos);
        return context == null ? ArmorDamageState.effectiveToughness(level, pos, state, fallback)
                : context.dynamicToughness();
    }

    public static double hardness(Level level, BlockState state, BlockPos pos, double fallback) {
        ArmorImpactContext context = matching(level, state, pos);
        return context == null ? fallback : context.dynamicHardness();
    }

    public double dynamicToughness() {
        return profile.blocks() == 0 ? Double.POSITIVE_INFINITY : profile.totalToughness();
    }

    public double dynamicHardness() {
        return profile.blocks() == 0 ? Double.POSITIVE_INFINITY : profile.effectiveHardness();
    }

    /** Original material value; use effectiveBlockToughness for damage-aware mass debit. */
    public double baseToughness() { return armor.toughness(); }

    /** Full struck-block toughness, not the DDA's partial segment contribution. */
    public double effectiveBlockToughness() { return effectiveBlockToughness; }

    public BlockDamageSavedData.ImpactDamage impactDamage() {
        return new BlockDamageSavedData.ImpactDamage(projectileMass, speed, speed * cosine,
                shell.penetration(), armor.hardness(), effectiveBlockToughness, cosine);
    }

    public double hardnessMultiplier() {
        return 1 + Math.max(0, dynamicHardness() - shell.penetration());
    }

    public double massCost() {
        return ArmorImpactPhysics.massCost(effectiveBlockToughness,
                dynamicHardness() - shell.penetration(), speed, cosine);
    }

    public double bounceChance(double baseChance) {
        return ArmorImpactPhysics.bounceChance(baseChance, cosine, shell.deflection(), dynamicHardness(), shell.penetration());
    }

    @Override
    public void close() {
        if (closed) return;
        if (ACTIVE.get() != this) throw new IllegalStateException("Armor impact scopes must close in reverse order");
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
        closed = true;
    }
}
