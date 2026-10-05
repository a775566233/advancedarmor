package org.wgx.advancedarmor.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wgx.advancedarmor.BlockDamageSavedData;

/** Clear immediately, including mining and re-placing the same material within one tick. */
@Mixin(LevelChunk.class)
public abstract class ArmorDamageRemovalMixin {
    @Shadow public abstract Level getLevel();

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void advancedarmor$clearDamage(BlockPos pos, BlockState state, boolean moving,
                                          CallbackInfoReturnable<BlockState> callback) {
        if (callback.getReturnValue() != null && getLevel() instanceof ServerLevel server)
            BlockDamageSavedData.get(server).remove(server, pos);
    }
}
