package org.wgx.advancedarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Advancedarmor.MODID)
@PrefixGameTestTemplate(false)
public final class CamouflageGameTests {
    private static CamouflageArmorBlockEntity armor(GameTestHelper helper, BlockPos relativePos) {
        helper.setBlock(relativePos, Advancedarmor.block("kc_armor").get());
        return (CamouflageArmorBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relativePos));
    }

    @GameTest(template = "empty")
    public static void visualNeighboursAndFaceCulling(GameTestHelper helper) {
        var center = armor(helper, new BlockPos(3, 3, 3));
        var east = armor(helper, new BlockPos(4, 3, 3));
        var above = armor(helper, new BlockPos(3, 4, 3));
        var diagonal = armor(helper, new BlockPos(4, 4, 3));
        var glass = Blocks.GLASS.defaultBlockState();
        var redGlass = Blocks.RED_STAINED_GLASS.defaultBlockState();
        center.setCamouflageState(glass);
        east.setCamouflageState(glass);
        above.setCamouflageState(redGlass);
        diagonal.setCamouflageState(glass);
        helper.setBlock(3, 3, 4, Blocks.GLASS);
        helper.setBlock(3, 3, 2, Blocks.STONE);
        armor(helper, new BlockPos(2, 3, 3));
        helper.setBlock(3, 2, 3, Blocks.AIR);
        var level = helper.getLevel();
        var pos = center.getBlockPos();
        var view = new CamouflageRenderView(level, pos, glass);

        helper.assertTrue(view.getBlockState(pos) == glass
                        && view.getBlockState(pos.east()) == glass
                        && view.getBlockState(pos.above()) == redGlass
                        && view.getBlockState(pos.east().above()) == glass
                        && view.getBlockState(pos.south()) == glass
                        && view.getBlockState(pos.west()).getBlock() instanceof ArmorBlock,
                "Texture connection queries must see each neighbour's own material, including diagonals");
        helper.assertTrue(!Block.shouldRenderFace(glass, view, pos, Direction.EAST, pos.east())
                        && !Block.shouldRenderFace(glass, view, pos, Direction.SOUTH, pos.south())
                        && !Block.shouldRenderFace(glass, view, pos, Direction.NORTH, pos.north())
                        && Block.shouldRenderFace(glass, view, pos, Direction.UP, pos.above())
                        && Block.shouldRenderFace(glass, view, pos, Direction.DOWN, pos.below()),
                "Cull matching glass and opaque neighbours but preserve faces beside different glass and air");
        var real = level.getBlockState(pos);
        helper.assertTrue(real.getBlock() instanceof ArmorBlock
                        && level.getBlockState(pos.east()).getBlock() instanceof ArmorBlock
                        && ArmorData.get(real.getBlock()).toughness() == 54,
                "Appearance queries must preserve the real armor and its material properties");
        for (Direction face : Direction.values()) {
            BlockPos sample = pos.relative(face);
            helper.assertTrue(view.getShade(face, true) == level.getShade(face, true)
                            && view.getBrightness(LightLayer.SKY, sample) == level.getBrightness(LightLayer.SKY, sample)
                            && view.getBrightness(LightLayer.BLOCK, sample) == level.getBrightness(LightLayer.BLOCK, sample)
                            && view.getRawBrightness(sample, 0) == level.getRawBrightness(sample, 0),
                    "Each face must retain the real world's lighting samples: " + face);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void connectedTextureAppearanceChangesAndReloads(GameTestHelper helper) {
        var armor = armor(helper, new BlockPos(2, 3, 2));
        var level = helper.getLevel();
        var pos = armor.getBlockPos();
        // Use Create's actual connected block, without loading client model classes on the server.
        var material = BuiltInRegistries.BLOCK.get(new ResourceLocation("create", "industrial_iron_block"))
                .defaultBlockState();
        helper.assertTrue(!material.isAir(), "Create's connected industrial iron block must be registered");
        armor.setCamouflageState(material);
        var firstModelData = armor.getModelData();
        var real = level.getBlockState(pos);
        helper.assertTrue(real.getAppearance(level, pos, Direction.UP, material, pos.east()) == material,
                "Create's getAppearance neighbour query must see camouflaged industrial iron");
        var saved = armor.getUpdateTag();
        armor.setCamouflageState(Blocks.STONE.defaultBlockState());
        var view = new CamouflageRenderView(level, pos.east(), material);
        helper.assertTrue(view.getBlockState(pos).is(Blocks.STONE)
                        && real.getAppearance(level, pos, Direction.UP, material, pos.east()).is(Blocks.STONE)
                        && firstModelData != armor.getModelData(),
                "Changing material must update both render neighbours and the immutable model data snapshot");
        armor.load(saved);
        helper.assertTrue(view.getBlockState(pos) == material
                        && real.getAppearance(level, pos, Direction.UP, material, pos.east()) == material,
                "Loading saved or network appearance must refresh connected texture inputs");
        armor.setCamouflageState(null);
        real = level.getBlockState(pos);
        helper.assertTrue(!real.getValue(ArmorBlock.CAMOUFLAGED)
                        && real.getAppearance(level, pos, Direction.UP, material, pos.east()) == real
                        && view.getBlockState(pos) == real,
                "Removing camouflage must restore the armor appearance for neighbours");
        helper.succeed();
    }
}
