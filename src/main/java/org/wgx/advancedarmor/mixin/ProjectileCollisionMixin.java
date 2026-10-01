package org.wgx.advancedarmor.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.wgx.advancedarmor.ArmorImpactContext;
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
        try (ArmorImpactContext ignored = ArmorImpactContext.open(projectile, state, hit)) {
            return ((CannonProjectileAccessor) projectile).advancedarmor$invokePenetration(context, state, hit);
        }
    }
}
