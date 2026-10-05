package org.wgx.advancedarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Armor block variant that can carry a per-position camouflage appearance. */
public final class ArmorBlock extends Block implements EntityBlock {
    public static final BooleanProperty CAMOUFLAGED = BooleanProperty.create("camouflaged");

    public ArmorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CAMOUFLAGED, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CAMOUFLAGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CamouflageArmorBlockEntity(pos, state);
    }
}
