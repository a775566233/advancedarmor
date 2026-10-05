package org.wgx.advancedarmor.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.wgx.advancedarmor.ArmorImpactContext;
import org.wgx.advancedarmor.BlockDamageSavedData;
import rbasamoyai.createbigcannons.config.CBCCfgMunitions;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;

/** Scope the call site, so a subclass need not call the CBC penetration implementation. */
@Mixin(value = AbstractCannonProjectile.class, remap = false)
public abstract class ProjectileCollisionMixin {
    @Redirect(method = "clipAndDamage", at = @At(value = "INVOKE", target =
            "Lrbasamoyai/createbigcannons/munitions/AbstractCannonProjectile;calculateBlockPenetration(Lrbasamoyai/createbigcannons/munitions/ProjectileContext;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/BlockHitResult;)Lrbasamoyai/createbigcannons/munitions/AbstractCannonProjectile$ImpactResult;"))
    private AbstractCannonProjectile.ImpactResult advancedarmor$impact(AbstractCannonProjectile projectile,
            ProjectileContext context, BlockState state, BlockHitResult hit) {
        // Always unwind, including addon exceptions and nested impact calls.
        try (ArmorImpactContext snapshot = ArmorImpactContext.open(projectile, state, hit)) {
            AbstractCannonProjectile.ImpactResult impactResult = ((CannonProjectileAccessor) projectile).advancedarmor$invokePenetration(context, state, hit);
            if (projectile.level() instanceof ServerLevel server
                    && ArmorImpactContext.current(projectile) != null
                    && context.griefState() == CBCCfgMunitions.GriefState.ALL_DAMAGE) {
                BlockDamageSavedData.get(server).applyImpact(server, hit.getBlockPos(), state, snapshot.impactDamage());
            }
            return impactResult;
        }
    }
}
