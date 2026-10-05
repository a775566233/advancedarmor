package org.wgx.advancedarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Common read API; client multipliers come from the server, never local config. */
public final class ArmorDamageState {
    private static final Map<Level, Map<Long, Status>> CLIENT = new WeakHashMap<>();
    private static int clientMaxLevel;
    private ArmorDamageState() {}

    public record Status(BlockPos pos, BlockState state, int level, int maxLevel, double multiplier, int crackId) {
        public int crackStage() {
            return level == 0 ? -1 : Math.min(9, Math.max(0, (int) Math.ceil(10.0 * level / maxLevel) - 1));
        }
    }

    public static Status get(Level level, BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) {
            int damage = BlockDamageSavedData.get(server).damageLevel(pos, state);
            return new Status(pos, state, damage, Config.ARMOR_DAMAGE_MAX_LEVEL.get(),
                    BlockDamageSavedData.toughnessMultiplier(damage), 0);
        }
        Status status = CLIENT.getOrDefault(level, Map.of()).get(pos.asLong());
        return status != null && status.state().equals(state) ? status : new Status(pos, state, 0, clientMaxLevel, 1, 0);
    }

    public static void setClientMaxLevel(int maxLevel) { clientMaxLevel = maxLevel; }

    public static double effectiveToughness(Level level, BlockPos pos, BlockState state, double base) {
        return base * get(level, pos, state).multiplier();
    }

    public static void updateClient(Level level, Status status) {
        Map<Long, Status> entries = CLIENT.computeIfAbsent(level, key -> new HashMap<>());
        Status previous = entries.remove(status.pos().asLong());
        if (previous != null && previous.crackId() != status.crackId())
            level.destroyBlockProgress(previous.crackId(), previous.pos(), -1);
        if (status.level() > 0) entries.put(status.pos().asLong(), status);
        level.destroyBlockProgress(status.crackId(), status.pos(), status.crackStage());
    }

    public static void clearClientChunk(Level level, ChunkPos chunk) {
        Map<Long, Status> entries = CLIENT.get(level);
        if (entries == null) return;
        entries.values().removeIf(status -> {
            if (!new ChunkPos(status.pos()).equals(chunk)) return false;
            level.destroyBlockProgress(status.crackId(), status.pos(), -1);
            return true;
        });
    }
}
