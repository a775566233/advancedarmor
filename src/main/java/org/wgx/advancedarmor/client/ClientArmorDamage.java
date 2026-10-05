package org.wgx.advancedarmor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.wgx.advancedarmor.Advancedarmor;
import org.wgx.advancedarmor.ArmorDamageState;
import org.wgx.advancedarmor.ArmorNetwork;

/** Client-only entry point, kept out of server packet class loading. */
@Mod.EventBusSubscriber(modid = Advancedarmor.MODID, value = Dist.CLIENT)
public final class ClientArmorDamage {
    private ClientArmorDamage() {}

    public static void apply(ArmorNetwork.DamageSync packet) {
        Level level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        if (packet.reset()) ArmorDamageState.clearClientChunk(level, packet.chunk());
        for (ArmorDamageState.Status entry : packet.entries()) ArmorDamageState.updateClient(level, entry);
    }

    @SubscribeEvent
    public static void unloadChunk(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide)
            ArmorDamageState.clearClientChunk(level, event.getChunk().getPos());
    }
}
