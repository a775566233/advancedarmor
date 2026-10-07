package org.wgx.advancedarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;
import org.wgx.advancedarmor.CamouflageArmorBlockEntity;
import org.wgx.advancedarmor.CamouflageRenderView;

/** Renders the stored appearance while retaining the armor block's collision and physics. */
public final class CamouflageArmorRenderer implements BlockEntityRenderer<CamouflageArmorBlockEntity> {
    private final BlockRenderDispatcher dispatcher;

    public CamouflageArmorRenderer(BlockEntityRendererProvider.Context context) {
        dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(CamouflageArmorBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState appearance = entity.camouflageState();
        // The armor model is rendered by the chunk renderer when uncamouflaged;
        // this renderer only supplies the replacement model.
        if (appearance == null || entity.getLevel() == null) return;
        var level = entity.getLevel();
        var pos = entity.getBlockPos();
        var view = new CamouflageRenderView(level, pos, appearance);
        var model = dispatcher.getBlockModel(appearance);
        var modelData = model.getModelData(view, pos, appearance, ModelData.EMPTY);
        long seed = appearance.getSeed(pos);
        var random = RandomSource.create(seed);
        for (var renderType : model.getRenderTypes(appearance, random, modelData)) {
            // World tessellation already applies directional shade and AO.
            // Use the BLOCK vertex format/shader, as vanilla falling blocks do;
            // an entity shader adds a second directional light to these colours.
            poseStack.pushPose();
            try {
                random.setSeed(seed);
                dispatcher.renderBatched(appearance, pos, view, poseStack,
                        buffers.getBuffer(RenderTypeHelper.getMovingBlockRenderType(renderType)),
                        true, random, modelData, renderType);
            } finally {
                poseStack.popPose();
            }
        }
    }
}
