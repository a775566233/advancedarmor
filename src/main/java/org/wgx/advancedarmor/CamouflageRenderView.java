package org.wgx.advancedarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.model.data.ModelDataManager;

/** Visual neighbours for connected textures, face culling and ambient occlusion. */
public final class CamouflageRenderView implements BlockAndTintGetter {
    private final BlockAndTintGetter level;
    private final BlockPos origin;
    private final BlockState appearance;

    public CamouflageRenderView(BlockAndTintGetter level, BlockPos origin, BlockState appearance) {
        this.level = level;
        this.origin = origin.immutable();
        this.appearance = appearance;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (origin.equals(pos)) return appearance;
        BlockState state = level.getBlockState(pos);
        return CamouflageArmorBlockEntity.appearanceAt(level, pos, state);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return level.getBlockEntity(pos);
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return level.getMinBuildHeight();
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return level.getShade(direction, shade);
    }

    @Override
    public float getShade(float normalX, float normalY, float normalZ, boolean shade) {
        return level.getShade(normalX, normalY, normalZ, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return level.getBrightness(layer, pos);
    }

    @Override
    public int getRawBrightness(BlockPos pos, int skyDarken) {
        return level.getRawBrightness(pos, skyDarken);
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return level.getBlockTint(pos, resolver);
    }

    @Override
    public ModelDataManager getModelDataManager() {
        return level.getModelDataManager();
    }
}
