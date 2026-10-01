package org.wgx.advancedarmor.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.wgx.advancedarmor.ArmorImpactContext;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesProvider;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;

/** Hook stable calls rather than local slots, which differ in edited CBC builds. */
@Mixin(value = AbstractBigCannonProjectile.class, remap = false)
public abstract class CannonProjectileMixin {
    @Redirect(method = "calculateBlockPenetration", at = @At(value = "INVOKE", target =
            "Lrbasamoyai/createbigcannons/block_armor_properties/BlockArmorPropertiesProvider;toughness(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Z)D"))
    private double advancedarmor$gate(BlockArmorPropertiesProvider provider, Level level,
                                      BlockState state, BlockPos pos, boolean recurse) {
        double toughness = provider.toughness(level, state, pos, recurse);
        ArmorImpactContext context = ArmorImpactContext.current((AbstractCannonProjectile) (Object) this);
        return context == null ? toughness : toughness * context.hardnessMultiplier();
    }

    // CBC's first mass write is the penetrating branch; subsequent writes stop
    // or bounce the projectile. Replace its debit before clamping destroys the
    // original mass, without depending on the mass-cost local variable's slot.
    @ModifyArg(method = "calculateBlockPenetration", at = @At(value = "INVOKE", target =
            "Lrbasamoyai/createbigcannons/munitions/big_cannon/AbstractBigCannonProjectile;setProjectileMass(F)V", ordinal = 0), index = 0)
    private float advancedarmor$singleBlockMassCost(float originalMass) {
        AbstractCannonProjectile projectile = (AbstractCannonProjectile) (Object) this;
        ArmorImpactContext context = ArmorImpactContext.current(projectile);
        return context == null ? originalMass : (float) Math.max(0, projectile.getProjectileMass() - context.massCost());
    }

    @Redirect(method = "calculateBlockPenetration", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/util/RandomSource;nextDouble()D", remap = true))
    private double advancedarmor$bounce(RandomSource random) {
        double sample = random.nextDouble();
        ArmorImpactContext context = ArmorImpactContext.current((AbstractCannonProjectile) (Object) this);
        if (context == null) return sample;
        // The caller still owns all bounce eligibility checks. Its comparison
        // uses a CBC-version-specific probability; select the armor decision
        // with one random draw, then force that comparison to the same result.
        double chance = context.bounceChance(CBCConfigs.SERVER.munitions.baseProjectileBounceChance.getF());
        return sample < chance ? -1 : Double.POSITIVE_INFINITY;
    }
}
