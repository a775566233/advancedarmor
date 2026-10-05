package org.wgx.advancedarmor.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.wgx.advancedarmor.Advancedarmor;

@Mod.EventBusSubscriber(modid = Advancedarmor.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AdvancedarmorClient {
    private AdvancedarmorClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Advancedarmor.CAMOUFLAGE_BLOCK_ENTITY.get(), CamouflageArmorRenderer::new);
    }
}
