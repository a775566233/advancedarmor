package org.wgx.advancedarmor.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wgx.advancedarmor.ArmorData;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesProvider;

import java.util.List;

/** Replaces the CBC provider only for blocks declared by an armor data pack. */
@Mixin(value = BlockArmorPropertiesHandler.class, remap = false)
public abstract class ArmorProviderMixin {

    @Inject(method = "getProperties(Lnet/minecraft/world/level/block/state/BlockState;)Lrbasamoyai/createbigcannons/block_armor_properties/BlockArmorPropertiesProvider;", at = @At("RETURN"), cancellable = true)
    private static void advancedarmor$properties(BlockState state, CallbackInfoReturnable<BlockArmorPropertiesProvider> callback) {
        ArmorData.Values values = ArmorData.get(state.getBlock());
        if (values == null) return;
        callback.setReturnValue(new BlockArmorPropertiesProvider() {
            @Override
            public double hardness(Level level, BlockState block, BlockPos pos, boolean recurse) {
                return values.hardness();
            }

            @Override
            public double toughness(Level level, BlockState block, BlockPos pos, boolean recurse) {
                return values.toughness();
            }

            @Override
            public List<BlockState> containedBlockStates(Level level, BlockState block, BlockPos pos, boolean recurse) {
                return List.of(block);
            }
        });
    }
}
