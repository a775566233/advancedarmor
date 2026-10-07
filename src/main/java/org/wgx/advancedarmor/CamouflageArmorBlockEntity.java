package org.wgx.advancedarmor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Stores only the visual block state; the containing armor block remains unchanged. */
public final class CamouflageArmorBlockEntity extends BlockEntity {
    private static final String CAMOUFLAGE_STATE = "CamouflageState";
    private static final ModelProperty<BlockState> APPEARANCE = new ModelProperty<>();
    private BlockState camouflageState;
    private volatile ModelData modelData = ModelData.EMPTY;

    public CamouflageArmorBlockEntity(BlockPos pos, BlockState state) {
        super(Advancedarmor.CAMOUFLAGE_BLOCK_ENTITY.get(), pos, state);
    }

    public BlockState camouflageState() {
        return camouflageState;
    }

    public static BlockState appearanceAt(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ArmorBlock)) return state;
        var manager = level.getModelDataManager();
        if (manager != null) {
            // Chunk model compilation can run off-thread: use Forge's snapshot.
            var data = manager.getAt(pos);
            BlockState appearance = data == null ? null : data.get(APPEARANCE);
            return appearance == null ? state : appearance;
        }
        if (level.getBlockEntity(pos) instanceof CamouflageArmorBlockEntity armor
                && armor.camouflageState != null) return armor.camouflageState;
        return state;
    }

    @Override
    public ModelData getModelData() {
        return modelData;
    }

    private void refreshAppearance() {
        modelData = camouflageState == null ? ModelData.EMPTY
                : ModelData.builder().with(APPEARANCE, camouflageState).build();
        requestModelDataUpdate();
        if (level != null && level.isClientSide) {
            // A new material also changes the CT data of ordinary neighbours.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setCamouflageState(BlockState state) {
        camouflageState = state;
        refreshAppearance();
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
        refreshAppearance();
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
