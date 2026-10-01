package org.wgx.advancedarmor.mixin;

import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Radius is private in 1.20.1; the accessor is remapped for production jars. */
@Mixin(Explosion.class)
public interface ExplosionAccessor {
    @Accessor("radius") float advancedarmor$getRadius();
}
