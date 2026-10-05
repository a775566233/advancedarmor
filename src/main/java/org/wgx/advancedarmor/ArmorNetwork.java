package org.wgx.advancedarmor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.wgx.advancedarmor.client.ClientArmorDamage;

/** Dedicated servers synchronize both login and /reload data; clients never choose combat values. */
@Mod.EventBusSubscriber(modid = Advancedarmor.MODID)
public final class ArmorNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Advancedarmor.MODID, "armor_data"), () -> "2", "2"::equals, "2"::equals);

    public static void register() {
        CHANNEL.registerMessage(0, Sync.class, Sync::encode, Sync::decode, Sync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, DamageSync.class, DamageSync::encode, DamageSync::decode, DamageSync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendDamage(ServerLevel level, ArmorDamageState.Status status) {
        ChunkPos chunk = new ChunkPos(status.pos());
        DamageSync packet = new DamageSync(level.dimension().location(), chunk, false, List.of(status));
        // Ask the chunk map for watchers without loading the chunk, including VS shipyard chunks.
        level.getChunkSource().chunkMap.getPlayers(chunk, false).forEach(player ->
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet));
    }

    @SubscribeEvent
    public static void watchChunk(ChunkWatchEvent.Watch event) {
        sendDamageSnapshot(event.getLevel(), event.getPlayer(), event.getPos());
    }

    @SubscribeEvent
    public static void unwatchChunk(ChunkWatchEvent.UnWatch event) {
        CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer),
                new DamageSync(event.getLevel().dimension().location(), event.getPos(), true, List.of()));
    }

    private static void sendDamageSnapshot(ServerLevel level, ServerPlayer player, ChunkPos chunk) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DamageSync(level.dimension().location(),
                chunk, true, BlockDamageSavedData.get(level).snapshot(chunk)));
    }

    public record DamageSync(ResourceLocation dimension, ChunkPos chunk, boolean reset,
                             List<ArmorDamageState.Status> entries) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(dimension);
            buffer.writeInt(chunk.x);
            buffer.writeInt(chunk.z);
            buffer.writeBoolean(reset);
            buffer.writeVarInt(entries.size());
            for (ArmorDamageState.Status status : entries) {
                buffer.writeBlockPos(status.pos());
                buffer.writeVarInt(Block.getId(status.state()));
                buffer.writeVarInt(status.level());
                buffer.writeVarInt(status.maxLevel());
                buffer.writeDouble(status.multiplier());
                buffer.writeInt(status.crackId());
            }
        }

        static DamageSync decode(FriendlyByteBuf buffer) {
            ResourceLocation dimension = buffer.readResourceLocation();
            ChunkPos chunk = new ChunkPos(buffer.readInt(), buffer.readInt());
            boolean reset = buffer.readBoolean();
            int count = buffer.readVarInt();
            if (count < 0 || count > 65536) throw new IllegalArgumentException("Invalid damage entry count");
            List<ArmorDamageState.Status> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                BlockPos pos = buffer.readBlockPos();
                BlockState state = Block.stateById(buffer.readVarInt());
                int damage = buffer.readVarInt(), max = buffer.readVarInt();
                double multiplier = buffer.readDouble();
                int crackId = buffer.readInt();
                if (!new ChunkPos(pos).equals(chunk) || max < 1 || max > 32 || damage < 0 || damage > max
                        || !Double.isFinite(multiplier) || multiplier < 0 || multiplier > 1 || crackId >= 0)
                    throw new IllegalArgumentException("Invalid armor damage status");
                entries.add(new ArmorDamageState.Status(pos, state, damage, max, multiplier, crackId));
            }
            return new DamageSync(dimension, chunk, reset, List.copyOf(entries));
        }

        private void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientArmorDamage.apply(this)));
            context.get().setPacketHandled(true);
        }
    }

    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        Sync packet = new Sync(ArmorData.snapshot(), Config.ARMOR_DAMAGE_MAX_LEVEL.get());
        if (event.getPlayer() != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), packet);
        } else {
            event.getPlayerList().getPlayers().forEach(player -> CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet));
        }
    }

    private record Sync(Map<Block, ArmorData.Values> values, int maxDamageLevel) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(maxDamageLevel);
            buffer.writeVarInt(values.size());
            values.forEach((block, armor) -> {
                buffer.writeResourceLocation(ForgeRegistries.BLOCKS.getKey(block));
                buffer.writeDouble(armor.hardness());
                buffer.writeDouble(armor.toughness());
                buffer.writeDouble(armor.explosionResistance());
            });
        }

        private static Sync decode(FriendlyByteBuf buffer) {
            int maxDamageLevel = buffer.readVarInt();
            if (maxDamageLevel < 1 || maxDamageLevel > 32) throw new IllegalArgumentException("Invalid maximum damage level");
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
            return new Sync(Map.copyOf(result), maxDamageLevel);
        }

        private void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ArmorData.replace(values);
                ArmorDamageState.setClientMaxLevel(maxDamageLevel);
            });
            context.get().setPacketHandled(true);
        }
    }
}
