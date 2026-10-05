package org.wgx.advancedarmor;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Dynamic recipe: the output stores the block state supplied by the second input. */
public final class CamouflageMoldRecipe extends CustomRecipe {
    public CamouflageMoldRecipe(ResourceLocation id) {
        super(id, CraftingBookCategory.MISC);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return find(container) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, net.minecraft.core.RegistryAccess registryAccess) {
        BlockState state = find(container);
        if (state == null) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(Advancedarmor.CAMOUFLAGE_MOLD.get());
        CamouflageMoldItem.setTarget(result, state);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registryAccess) {
        return new ItemStack(Advancedarmor.CAMOUFLAGE_MOLD.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Advancedarmor.CAMOUFLAGE_MOLD_RECIPE.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    private static BlockState find(Container container) {
        ItemStack mold = ItemStack.EMPTY;
        ItemStack blockStack = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(Advancedarmor.CAMOUFLAGE_MOLD.get())) {
                if (CamouflageMoldItem.hasTarget(stack) || !mold.isEmpty()) return null;
                mold = stack;
            } else if (stack.getItem() instanceof BlockItem) {
                if (!blockStack.isEmpty()) return null;
                blockStack = stack;
            } else {
                return null;
            }
        }
        if (mold.isEmpty() || blockStack.isEmpty()) return null;
        Block block = ((BlockItem) blockStack.getItem()).getBlock();
        if (block instanceof ArmorBlock || block == net.minecraft.world.level.block.Blocks.AIR) return null;
        BlockState state = block.defaultBlockState();
        if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) return null;
        return state;
    }

    public static final class Serializer implements RecipeSerializer<CamouflageMoldRecipe> {
        @Override
        public CamouflageMoldRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new CamouflageMoldRecipe(id);
        }

        @Override
        public CamouflageMoldRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new CamouflageMoldRecipe(id);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, CamouflageMoldRecipe recipe) {
        }
    }
}
