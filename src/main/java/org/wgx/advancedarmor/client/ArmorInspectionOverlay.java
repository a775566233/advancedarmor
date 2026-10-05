package org.wgx.advancedarmor.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.wgx.advancedarmor.Advancedarmor;
import org.wgx.advancedarmor.ArmorInspection;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

/** Client-only HUD: holding the tool in either hand and aiming is sufficient. */
@Mod.EventBusSubscriber(modid = Advancedarmor.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArmorInspectionOverlay {
    private ArmorInspectionOverlay() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "armor_inspection", ArmorInspectionOverlay::render);
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen != null || minecraft.player == null
                || minecraft.level == null) return;
        if (!minecraft.player.getMainHandItem().is(Advancedarmor.ARMOR_INSPECTION_TOOL.get())
                && !minecraft.player.getOffhandItem().is(Advancedarmor.ARMOR_INSPECTION_TOOL.get())) return;
        Entity camera = minecraft.getCameraEntity();
        if (camera == null || !(minecraft.hitResult instanceof BlockHitResult hit)) return;

        ArmorInspection.Result result = ArmorInspection.inspect(minecraft.level, hit, camera.getViewVector(partialTick));
        if (result == null) return;
        Component toughness = Double.isFinite(result.toughness()) ? number(result.toughness())
                : Component.translatable("inspection.advancedarmor.trace_limit");
        List<Component> lines = new ArrayList<>(List.of(
                minecraft.level.getBlockState(hit.getBlockPos()).getBlock().getName().withStyle(ChatFormatting.AQUA),
                line("toughness", toughness),
                line("hardness", number(result.hardness())),
                line("blocks", Component.literal(Integer.toString(result.blocks()))),
                line("angle", Component.translatable("inspection.advancedarmor.degrees", number(result.angleDegrees())))));
        if (result.maxDamageLevel() > 0) {
            lines.add(line("damage", Component.literal(result.damageLevel() + "/" + result.maxDamageLevel())));
            lines.add(line("block_toughness", number(result.blockToughness())));
            lines.add(line("toughness_loss", Component.translatable("inspection.advancedarmor.percent",
                    number(result.toughnessLossPercent()))));
        }
        // Minecraft's tooltip layout wraps long translations and keeps the panel
        // on screen at small resolutions and large GUI scales.
        graphics.renderTooltip(minecraft.font, lines, Optional.empty(), width / 2 + 12, height / 2 + 24);
    }

    private static Component number(double value) {
        return Component.literal(String.format(Locale.ROOT, "%.2f", value));
    }

    private static Component line(String name, Component value) {
        return Component.translatable("inspection.advancedarmor." + name).withStyle(ChatFormatting.GRAY)
                .append(value.copy().withStyle(ChatFormatting.GOLD));
    }
}
