package org.wgx.advancedarmor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Dedicated servers synchronize both login and /reload data; clients never choose combat values. */
@Mod.EventBusSubscriber(modid = Advancedarmor.MODID)
public final class ArmorNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Advancedarmor.MODID, "armor_data"), () -> "1", "1"::equals, "1"::equals);

    public static void register() {
        CHANNEL.registerMessage(0, Sync.class, Sync::encode, Sync::decode, Sync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        Sync packet = new Sync(ArmorData.snapshot());
        if (event.getPlayer() != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), packet);
        } else {
            event.getPlayerList().getPlayers().forEach(player -> CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet));
        }
    }

    private record Sync(Map<Block, ArmorData.Values> values) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(values.size());
            values.forEach((block, armor) -> {
                buffer.writeResourceLocation(ForgeRegistries.BLOCKS.getKey(block));
                buffer.writeDouble(armor.hardness());
                buffer.writeDouble(armor.toughness());
                buffer.writeDouble(armor.explosionResistance());
            });
        }

        private static Sync decode(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > 65536) throw new IllegalArgumentException("Invalid armor data count");
            Map<Block, ArmorData.Values> result = new HashMap<>();
            for (int entry = 0; entry < count; entry++) {
                ResourceLocation id = buffer.readResourceLocation();
                double hardness = buffer.readDouble(), toughness = buffer.readDouble(), blast = buffer.readDouble();
                if (!Double.isFinite(hardness + toughness + blast) || hardness < 0 || toughness < 0 || blast < 0)
                    throw new IllegalArgumentException("Invalid armor properties");
                if (ForgeRegistries.BLOCKS.containsKey(id)) result.put(ForgeRegistries.BLOCKS.getValue(id), new ArmorData.Values(hardness, toughness, blast));
            }
            return new Sync(Map.copyOf(result));
        }

        private void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> ArmorData.replace(values));
            context.get().setPacketHandled(true);
        }
    }
}
