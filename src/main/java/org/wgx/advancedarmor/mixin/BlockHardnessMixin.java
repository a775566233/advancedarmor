package org.wgx.advancedarmor.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wgx.advancedarmor.ArmorData;

/** Mining hardness follows /reload for every block listed by an armor data pack. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockHardnessMixin {
    @Shadow public abstract Block getBlock();

    @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
    private void advancedarmor$hardness(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> callback) {
        ArmorData.Values values = ArmorData.get(getBlock());
        if (values != null) callback.setReturnValue((float) values.hardness());
    }
}
