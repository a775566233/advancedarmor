package org.wgx.advancedarmor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/** Data pack schema: block ID -> hardness, toughness, explosion_resistance. */
public final class ArmorData extends SimpleJsonResourceReloadListener {
    public record Values(double hardness, double toughness, double explosionResistance) {}
    private static volatile Map<Block, Values> values = Map.of();

    public ArmorData() { super(new com.google.gson.Gson(), "armor_properties"); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Block, Values> loaded = new HashMap<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = entry.getKey();
            JsonElement element = entry.getValue();
            try {
                JsonObject object = element.getAsJsonObject();
                ResourceLocation blockId = new ResourceLocation(object.get("block").getAsString());
                Block block = ForgeRegistries.BLOCKS.getValue(blockId);
                if (block == null || !ForgeRegistries.BLOCKS.containsKey(blockId)) {
                    AdvancedarmorLog.warn("Unknown block {} in armor data {}", blockId, id);
                    return;
                }
                double hardness = number(object, "hardness");
                double toughness = number(object, "toughness");
                double resistance = number(object, "explosion_resistance");
                if (hardness < 0 || toughness < 0 || resistance < 0) throw new IllegalArgumentException("negative armor value");
                loaded.put(block, new Values(hardness, toughness, resistance));
            } catch (RuntimeException ex) {
                AdvancedarmorLog.warn("Invalid armor data {}: {}", id, ex.getMessage());
            }
        });
        values = Map.copyOf(loaded);
    }

    private static double number(JsonObject object, String name) {
        double value = object.get(name).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite " + name);
        return value;
    }

    public static Values get(Block block) { return values.get(block); }
    static Map<Block, Values> snapshot() { return values; }
    static void replace(Map<Block, Values> incoming) { values = Map.copyOf(incoming); }
}
