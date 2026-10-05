package org.wgx.advancedarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;

/** A single-use-per-durability mold that copies a block state to an armor position. */
public final class CamouflageMoldItem extends Item {
    public static final String TARGET_STATE = "TargetState";

    public CamouflageMoldItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return Config.CAMOUFLAGE_MOLD_DURABILITY.get();
    }

    public static boolean hasTarget(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TARGET_STATE);
    }

    public static void setTarget(ItemStack stack, BlockState state) {
        stack.getOrCreateTag().put(TARGET_STATE, NbtUtils.writeBlockState(state));
        // The item model uses this predicate to switch from the empty mold
        // texture to the burned/filled mold texture.
        stack.getOrCreateTag().putInt("CustomModelData", 1);
    }

    public static BlockState targetState(ItemStack stack) {
        return hasTarget(stack)
                ? NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), stack.getTag().getCompound(TARGET_STATE))
                : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (hasTarget(stack)) {
            BlockState target = targetState(stack);
            if (target != null) {
                return Component.translatable("item.advancedarmor.camouflage_mold_named",
                        super.getName(stack), target.getBlock().getName());
            }
        }
        return super.getName(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState armorState = level.getBlockState(pos);
        if (!(armorState.getBlock() instanceof ArmorBlock) || !hasTarget(context.getItemInHand())) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        // Blocks placed before the camouflage feature may not have a block
        // entity yet. Create it lazily when the mold is used on such a block.
        if (blockEntity == null && !level.isClientSide) {
            blockEntity = new CamouflageArmorBlockEntity(pos, armorState);
            level.setBlockEntity(blockEntity);
        }
        if (!(blockEntity instanceof CamouflageArmorBlockEntity camouflage)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            camouflage.setCamouflageState(targetState(context.getItemInHand()));
            context.getItemInHand().hurtAndBreak(1, context.getPlayer(),
                    player -> player.broadcastBreakEvent(context.getHand()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
