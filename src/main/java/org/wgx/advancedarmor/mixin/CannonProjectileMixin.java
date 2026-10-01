package org.wgx.advancedarmor.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wgx.advancedarmor.ArmorData;
import org.wgx.advancedarmor.ArmorImpactPhysics;
import org.wgx.advancedarmor.ArmorPhysics;
import rbasamoyai.createbigcannons.config.CBCCfgMunitions;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.network.ClientboundPlayBlockHitEffectPacket;
import rbasamoyai.createbigcannons.utils.CBCUtils;

/**
 * Apply the armor plate's continuous thickness to CBC's penetration gate while
 * retaining CBC's per-impact mass accounting and collision lifecycle.
 */
@Mixin(value = {AbstractBigCannonProjectile.class, AbstractAutocannonProjectile.class}, remap = false)
public abstract class CannonProjectileMixin extends AbstractCannonProjectile {
    protected CannonProjectileMixin(EntityType<? extends AbstractCannonProjectile> type, Level level) { super(type, level); }

    @Inject(method = "calculateBlockPenetration", at = @At("HEAD"), cancellable = true)
    private void advancedarmor$impact(ProjectileContext context, BlockState state, BlockHitResult hit,
                                      CallbackInfoReturnable<ImpactResult> callback) {
        ArmorData.Values armor = ArmorData.get(state.getBlock());
        if (armor == null) return;
        Vec3 velocity = getDeltaMovement().add(getForces(position(), getDeltaMovement()));
        double speed = velocity.length();
        BallisticPropertiesComponent shell = getBallisticProperties();
        Vec3 normal = CBCUtils.getSurfaceNormalVector(level(), hit);
        double cosine = Math.max(0, -velocity.normalize().dot(normal));
        ArmorPhysics.Profile plate = ArmorPhysics.trace(level(), hit, velocity);
        double hardnessDifference = armor.hardness() - shell.penetration();
        double hardnessMultiplier = 1 + Math.max(0, hardnessDifference);
        double bonus = 1 + Math.max(0, speed - CBCConfigs.SERVER.munitions.minVelocityForPenetrationBonus.getF())
                * CBCConfigs.SERVER.munitions.penetrationBonusScale.getF();
        // CBC uses only the velocity component normal to the impact face.
        // Omitting cosine let grazing shots keep their full penetration budget.
        double budget = ArmorImpactPhysics.penetrationBudget(getProjectileMass(), speed, cosine, bonus);
        // DDA path length already includes the oblique 1/cos(angle) factor. Use
        // the complete contiguous plate here: the first collision represents the
        // effective thickness behind that impact, rather than only one voxel.
        // CBC charges an additional multiplier when armor hardness exceeds the
        // shell's penetration rating; omitting it made hard armor too cheap.
        double dynamicCost = plate.totalToughness() * hardnessMultiplier;
        boolean penetrates = speed > 1.0e-4 && plate.blocks() > 0
                && budget > dynamicCost
                && context.griefState() != CBCCfgMunitions.GriefState.NO_DAMAGE && state.getDestroySpeed(level(), hit.getBlockPos()) >= 0;
        double bounceChance = ArmorImpactPhysics.bounceChance(
                CBCConfigs.SERVER.munitions.baseProjectileBounceChance.getF(), cosine,
                shell.deflection(), armor.hardness(), shell.penetration());
        boolean bounces = canHitSurface() && CBCConfigs.SERVER.munitions.projectilesCanBounce.get()
                && level().random.nextDouble() < bounceChance;
        boolean shatters = canHitSurface() && !bounces && hardnessDifference > shell.toughness();
        ImpactResult.KinematicOutcome outcome = bounces ? ImpactResult.KinematicOutcome.BOUNCE
                : penetrates ? ImpactResult.KinematicOutcome.PENETRATE : ImpactResult.KinematicOutcome.STOP;

        state.onProjectileHit(level(), state, hit, this);
        if (!level().isClientSide) {
            Vec3 contact = ArmorPhysics.worldContact(level(), hit);
            Vec3 debris = bounces ? velocity.subtract(normal.scale(1.7 * velocity.dot(normal))) : velocity.reverse();
            context.addPlayedEffect(new ClientboundPlayBlockHitEffectPacket(state, getType(), bounces, true,
                    contact.x, contact.y, contact.z, (float) debris.x, (float) debris.y, (float) debris.z));
            if (outcome == ImpactResult.KinematicOutcome.PENETRATE && !shatters) {
                // CBC debits the hit block's full toughness using normal speed.
                // DDA thickness affects the dynamic gate above, not this debit.
                double massCost = ArmorImpactPhysics.massCost(
                        armor.toughness(), hardnessDifference, speed, cosine);
                setProjectileMass((float) Math.max(0, getProjectileMass() - massCost));
                level().setBlock(hit.getBlockPos(), Blocks.AIR.defaultBlockState(), 11);
            } else if (!bounces) {
                setProjectileMass(0);
            }
        }
        // CBC normally calls this after calculateBlockPenetration. Since the mixin
        // replaces that method, forward the impact to shell fuzes ourselves.
        ImpactResult.KinematicOutcome effectiveOutcome = outcome == ImpactResult.KinematicOutcome.PENETRATE && shatters
                ? ImpactResult.KinematicOutcome.STOP : outcome;
        boolean shouldRemove = shatters;
        shouldRemove |= onImpact(hit, new ImpactResult(effectiveOutcome, shatters), context);
        ImpactResult result = new ImpactResult(effectiveOutcome, shouldRemove);
        callback.setReturnValue(result);
    }
}
