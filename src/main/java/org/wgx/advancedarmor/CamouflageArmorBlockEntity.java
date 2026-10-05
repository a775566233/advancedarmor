package org.wgx.advancedarmor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stores only the visual block state; the containing armor block remains unchanged. */
public final class CamouflageArmorBlockEntity extends BlockEntity {
    private static final String CAMOUFLAGE_STATE = "CamouflageState";
    private BlockState camouflageState;

    public CamouflageArmorBlockEntity(BlockPos pos, BlockState state) {
        super(Advancedarmor.CAMOUFLAGE_BLOCK_ENTITY.get(), pos, state);
    }

    public BlockState camouflageState() {
        return camouflageState;
    }

    public void setCamouflageState(BlockState state) {
        camouflageState = state;
        setChanged();
        if (level != null) {
            BlockState current = level.getBlockState(worldPosition);
            if (current.getBlock() instanceof ArmorBlock
                    && current.getValue(ArmorBlock.CAMOUFLAGED) != (state != null)) {
                level.setBlock(worldPosition,
                        current.setValue(ArmorBlock.CAMOUFLAGED, state != null), 3);
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Upgrade old saves whose block entity has camouflage data but whose
        // block state predates the camouflaged property.
        if (level != null && camouflageState != null) {
            BlockState current = level.getBlockState(worldPosition);
            if (current.getBlock() instanceof ArmorBlock
                    && !current.getValue(ArmorBlock.CAMOUFLAGED)) {
                level.setBlock(worldPosition, current.setValue(ArmorBlock.CAMOUFLAGED, true), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (camouflageState != null) tag.put(CAMOUFLAGE_STATE, NbtUtils.writeBlockState(camouflageState));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        camouflageState = tag.contains(CAMOUFLAGE_STATE)
                ? NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound(CAMOUFLAGE_STATE))
                : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
