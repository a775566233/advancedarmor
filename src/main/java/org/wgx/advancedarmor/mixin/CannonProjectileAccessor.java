package org.wgx.advancedarmor.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

/** Invokers retain virtual dispatch to addon overrides of CBC's protected methods. */
@Mixin(value = AbstractCannonProjectile.class, remap = false)
public interface CannonProjectileAccessor {
    @Invoker("calculateBlockPenetration")
    AbstractCannonProjectile.ImpactResult advancedarmor$invokePenetration(
            ProjectileContext context, BlockState state, BlockHitResult hit);

    @Invoker("getForces")
    Vec3 advancedarmor$invokeForces(Vec3 position, Vec3 movement);

    @Invoker("getBallisticProperties")
    BallisticPropertiesComponent advancedarmor$invokeBallisticProperties();
}
