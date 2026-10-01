package org.wgx.advancedarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.wgx.advancedarmor.mixin.ExplosionAccessor;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;

import java.util.List;

/** Select before other Forge listeners, allowing claim/protection mods to filter our final list. */
@Mod.EventBusSubscriber(modid = Advancedarmor.MODID)
public final class BlastEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDetonate(ExplosionEvent.Detonate event) {
        Explosion explosion = event.getExplosion();
        if (event.getLevel().isClientSide || !(explosion instanceof ShellExplosion)) return;
        List<BlockPos> selected = BlastPropagation.select(event.getLevel(), explosion,
                ((ExplosionAccessor) explosion).advancedarmor$getRadius(), List.copyOf(event.getAffectedBlocks()));
        event.getAffectedBlocks().clear();
        event.getAffectedBlocks().addAll(selected);
    }
}
