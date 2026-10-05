package org.wgx.advancedarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.wgx.advancedarmor.Config.*;

/** Per-position damage, independent of the shared material data. */
public final class BlockDamageSavedData extends SavedData {
    public static final String SAVED_DATA_ID = "advancedarmor_block_damage";
    private static int nextCrackId = -1;
    private final Map<Long, DamageEntry> entries = new HashMap<>();

    private static final class DamageEntry {
        final BlockPos position;
        final BlockState fingerprint;
        final int crackId = nextCrackId--;
        int level;
        long lastHitTick;

        DamageEntry(BlockPos position, BlockState fingerprint, int level, long lastHitTick) {
            this.position = position.immutable();
            this.fingerprint = fingerprint;
            this.level = level;
            this.lastHitTick = lastHitTick;
        }
    }

    public record ImpactDamage(double projectileMass, double speed, double normalSpeed,
                               double penetration, double hardness, double toughness, double cosine) {
        public double impactEnergy() { return 0.5 * projectileMass * normalSpeed * normalSpeed; }
    }

    public static BlockDamageSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> load(level, tag),
                BlockDamageSavedData::new, SAVED_DATA_ID);
    }

    public int damageLevel(BlockPos pos, BlockState state) {
        DamageEntry entry = entries.get(pos.asLong());
        return ARMOR_DAMAGE_ENABLED.get() && entry != null && entry.fingerprint.equals(state) ? entry.level : 0;
    }

    public double getEffectiveToughness(ServerLevel level, BlockPos pos, BlockState state, double baseToughness) {
        return baseToughness * toughnessMultiplier(damageLevel(pos, state));
    }

    static double toughnessMultiplier(int damageLevel) {
        return Math.pow(Math.max(0, 1 - (double) damageLevel / ARMOR_DAMAGE_MAX_LEVEL.get()),
                ARMOR_DAMAGE_TOUGHNESS_EXPONENT.get());
    }

    public void remove(ServerLevel level, BlockPos pos) {
        DamageEntry entry = entries.remove(pos.asLong());
        if (entry != null) {
            level.destroyBlockProgress(entry.crackId, pos, -1);
            ArmorNetwork.sendDamage(level, new ArmorDamageState.Status(pos, entry.fingerprint, 0,
                    ARMOR_DAMAGE_MAX_LEVEL.get(), 1, entry.crackId));
            setDirty();
        }
    }

    /** Read only the pre-impact snapshot; CBC may already have spent the shell's mass. */
    public void applyImpact(ServerLevel level, BlockPos pos, BlockState state, ImpactDamage damage) {
        if (!ARMOR_DAMAGE_ENABLED.get() || ArmorData.get(state.getBlock()) == null) return;
        if (!level.getBlockState(pos).equals(state)) {
            remove(level, pos);
            return;
        }
        int added = rollDamageLevels(damage, level.random);
        if (added > 0) addDamageLevels(level, pos, state, added);
    }

    static double impactIntensity(ImpactDamage damage) {
        if (!(damage.projectileMass() > 0) || !(damage.normalSpeed() > 0)
                || !(damage.penetration() > 0) || !Double.isFinite(damage.impactEnergy())
                || !Double.isFinite(damage.hardness()) || !Double.isFinite(damage.toughness())) return 0;
        double penetrationRatio = Math.min(100, damage.penetration() / Math.max(damage.hardness(), 1.0e-6));
        double energy = Math.pow(damage.impactEnergy() / ARMOR_DAMAGE_REFERENCE_IMPACT.get(),
                ARMOR_DAMAGE_IMPACT_EXPONENT.get());
        double penetration = Math.pow(penetrationRatio, ARMOR_DAMAGE_PENETRATION_EXPONENT.get());
        double minToughness = ARMOR_DAMAGE_REFERENCE_TOUGHNESS.get() * ARMOR_DAMAGE_MIN_TOUGHNESS_FACTOR.get();
        double toughness = Mth.clamp(ARMOR_DAMAGE_REFERENCE_TOUGHNESS.get()
                / Math.max(damage.toughness(), minToughness), 0.25, 4);
        double bonus = 1 + ARMOR_DAMAGE_HARDNESS_BONUS_SCALE.get() * Math.max(0, penetrationRatio - 1);
        return Math.min(1.0e12, energy * penetration * toughness * bonus);
    }

    static double damageProbability(ImpactDamage damage) {
        return ARMOR_DAMAGE_MAX_PROBABILITY.get()
                * -Math.expm1(-ARMOR_DAMAGE_PROBABILITY_RATE.get() * impactIntensity(damage));
    }

    static int rollDamageLevels(ImpactDamage damage, RandomSource random) {
        if (random.nextDouble() >= damageProbability(damage)) return 0;
        double intensity = impactIntensity(damage);
        double extra = ARMOR_DAMAGE_EXTRA_LEVEL_PROBABILITY.get() * intensity / (1 + intensity);
        int added = 1;
        while (added < ARMOR_DAMAGE_MAX_LEVELS_PER_HIT.get() && random.nextDouble() < extra) added++;
        return added;
    }

    void addDamageLevels(ServerLevel level, BlockPos pos, BlockState state, int added) {
        if (added <= 0 || !level.getBlockState(pos).equals(state) || ArmorData.get(state.getBlock()) == null) return;
        DamageEntry entry = entries.get(pos.asLong());
        if (entry != null && !entry.fingerprint.equals(state)) {
            remove(level, pos);
            entry = null;
        }
        int max = ARMOR_DAMAGE_MAX_LEVEL.get();
        int updated = Math.min(max, (entry == null ? 0 : entry.level) + added);
        if (updated == max && ARMOR_DAMAGE_DESTROY_AT_MAX_LEVEL.get()) {
            remove(level, pos);
            level.destroyBlock(pos, true);
            return;
        }
        // A disabled destruction option must never leave a zero-toughness solid block.
        updated = Math.min(updated, ARMOR_DAMAGE_DESTROY_AT_MAX_LEVEL.get() ? max : max - 1);
        if (updated == 0) return;
        if (entry == null) {
            entry = new DamageEntry(pos, state, updated, level.getGameTime());
            entries.put(pos.asLong(), entry);
        }
        entry.level = updated;
        entry.lastHitTick = level.getGameTime();
        publish(level, entry);
        setDirty();
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        long interval = ARMOR_DAMAGE_DECAY_INTERVAL_TICKS.get();
        Iterator<DamageEntry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            DamageEntry entry = iterator.next();
            boolean loaded = level.hasChunkAt(entry.position);
            boolean invalid = loaded && (!level.getBlockState(entry.position).equals(entry.fingerprint)
                    || ArmorData.get(entry.fingerprint.getBlock()) == null);
            long elapsed = Math.max(0, now - entry.lastHitTick) / interval;
            if (invalid || elapsed >= entry.level) {
                iterator.remove();
                level.destroyBlockProgress(entry.crackId, entry.position, -1);
                ArmorNetwork.sendDamage(level, new ArmorDamageState.Status(entry.position, entry.fingerprint,
                        0, ARMOR_DAMAGE_MAX_LEVEL.get(), 1, entry.crackId));
                setDirty();
                continue;
            }
            if (elapsed > 0) {
                entry.level -= (int) elapsed;
                entry.lastHitTick += elapsed * interval;
                setDirty();
            }
            // Vanilla cracks expire; refresh them and the client snapshot while the chunk is loaded.
            if (loaded && (elapsed > 0 || now % 100 == 0)) publish(level, entry);
        }
    }

    private ArmorDamageState.Status status(DamageEntry entry) {
        int damageLevel = damageLevel(entry.position, entry.fingerprint);
        return new ArmorDamageState.Status(entry.position, entry.fingerprint, damageLevel,
                ARMOR_DAMAGE_MAX_LEVEL.get(), toughnessMultiplier(damageLevel), entry.crackId);
    }

    private void publish(ServerLevel level, DamageEntry entry) {
        ArmorDamageState.Status status = status(entry);
        level.destroyBlockProgress(entry.crackId, entry.position, status.crackStage());
        ArmorNetwork.sendDamage(level, status);
    }

    public List<ArmorDamageState.Status> snapshot(ChunkPos chunk) {
        List<ArmorDamageState.Status> result = new ArrayList<>();
        for (DamageEntry entry : entries.values()) {
            if (new ChunkPos(entry.position).equals(chunk)) result.add(status(entry));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (DamageEntry entry : entries.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", entry.position.asLong());
            tag.put("ExpectedState", NbtUtils.writeBlockState(entry.fingerprint));
            tag.putInt("Level", entry.level);
            tag.putLong("LastHitTick", entry.lastHitTick);
            list.add(tag);
        }
        root.put("Entries", list);
        return root;
    }

    public static BlockDamageSavedData load(ServerLevel level, CompoundTag root) {
        BlockDamageSavedData data = new BlockDamageSavedData();
        ListTag list = root.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), tag.getCompound("ExpectedState"));
            int damageLevel = Mth.clamp(tag.getInt("Level"), 0, ARMOR_DAMAGE_MAX_LEVEL.get());
            if (!state.isAir() && damageLevel > 0) {
                BlockPos pos = BlockPos.of(tag.getLong("Pos"));
                data.entries.put(pos.asLong(), new DamageEntry(pos, state, damageLevel, tag.getLong("LastHitTick")));
            }
        }
        return data;
    }
}
