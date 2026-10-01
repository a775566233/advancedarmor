package org.wgx.advancedarmor.mixin;

import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wgx.advancedarmor.ArmorData;

/** Data pack resistance also applies to vanilla/other explosions, not only CBC HE. */
@Mixin(Block.class)
public abstract class BlockExplosionMixin {
    @Inject(method = "getExplosionResistance", at = @At("HEAD"), cancellable = true)
    private void advancedarmor$resistance(CallbackInfoReturnable<Float> callback) {
        ArmorData.Values values = ArmorData.get((Block) (Object) this);
        if (values != null) callback.setReturnValue((float) values.explosionResistance());
    }
}
