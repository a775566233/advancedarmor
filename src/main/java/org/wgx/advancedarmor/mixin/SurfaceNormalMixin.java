package org.wgx.advancedarmor.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wgx.advancedarmor.compat.ShipSpace;
import rbasamoyai.createbigcannons.utils.CBCUtils;

/** CBC's hit Direction is local to the ship; reflection and impact angle need a world normal. */
@Mixin(value = CBCUtils.class, remap = false)
public abstract class SurfaceNormalMixin {
    @Inject(method = "getSurfaceNormalVector(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true)
    private static void advancedarmor$normal(Level level, BlockPos pos, Vec3 normal, CallbackInfoReturnable<Vec3> callback) {
        ShipSpace space = ShipSpace.at(level, pos);
        if (space != ShipSpace.WORLD) callback.setReturnValue(space.worldDirection(normal).normalize());
    }
}
