package org.wgx.advancedarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import org.wgx.advancedarmor.CamouflageArmorBlockEntity;

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
        if (appearance == null) return;
        BlockState stateToRender = appearance;
        int light = packedLight;
        if (entity.getLevel() != null) {
            // The armor block state is an air model while camouflaged. Use
            // the light one block above the position as the fallback so the
            // replacement model is not shaded as an opaque black cube.
            int worldLight = LevelRenderer.getLightColor(entity.getLevel(), entity.getBlockPos().above());
            light = brighterLight(light, worldLight);
        }
        dispatcher.renderSingleBlock(stateToRender, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
    }

    /** Combines packed block and sky light values without comparing the packed integer as a whole. */
    private static int brighterLight(int first, int second) {
        int blockLight = Math.max(first & 0xF0, second & 0xF0);
        int skyLight = Math.max(first & 0xF00000, second & 0xF00000);
        return blockLight | skyLight;
    }
}
