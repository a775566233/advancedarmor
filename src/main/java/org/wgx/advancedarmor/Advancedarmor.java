package org.wgx.advancedarmor;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Main registration. The armor data files supply the combat values. */
@Mod(Advancedarmor.MODID)
public final class Advancedarmor {
    public static final String MODID = "advancedarmor";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<Item> ARMOR_INSPECTION_TOOL = ITEMS.register("armor_inspection_tool",
            () -> new Item(new Item.Properties().stacksTo(1)));
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    private static final Map<String, RegistryObject<Block>> ARMOR = new LinkedHashMap<>();
    public static RegistryObject<Block> block(String name) { return ARMOR.get(name); }

    static {
        register("wrought_iron", 0.55f, 30);
        register("homogeneous_carbon_steel", 0.80f, 31);
        register("iron_steel_composite", 1.20f, 29);
        register("nickel_steel", 1.05f, 34);
        register("harvey_nickel_steel", 1.55f, 36);
        register("kc_armor", 1.95f, 41);
        register("knc_armor", 1.80f, 32);
        register("sts_armor", 1.15f, 40);
        register("ducol_steel", 0.85f, 32);
        register("british_plastic_protection", 0.25f, 18);
    }

    private static void register(String name, float hardness, float resistance) {
        RegistryObject<Block> block = BLOCKS.register(name, () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL).strength(hardness, resistance).requiresCorrectToolForDrops()));
        ARMOR.put(name, block);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public Advancedarmor() {
        ArmorNetwork.register();
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register("armor", () -> CreativeModeTab.builder()
                .withTabsBefore(CreativeModeTabs.COMBAT)
                .title(net.minecraft.network.chat.Component.translatable("itemGroup.advancedarmor.armor"))
                .icon(() -> ARMOR.get("kc_armor").get().asItem().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    ARMOR.values().forEach(block -> output.accept(block.get()));
                    output.accept(ARMOR_INSPECTION_TOOL.get());
                })
                .build());
        TABS.register(bus);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::addReloadListener);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER, Config.SPEC);
    }

    private void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ArmorData());
    }
}
