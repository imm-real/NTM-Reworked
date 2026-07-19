package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.BreedingReactorBlock;
import com.hbm.ntm.blockentity.BreedingReactorBlockEntity;
import com.hbm.ntm.blockentity.BreedingReactorProxyBlockEntity;
import com.hbm.ntm.item.BreedingRodItem;
import com.hbm.ntm.recipe.BreederRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BreedingReactorGameTests {
    private BreedingReactorGameTests() { }

    @GameTest(template = "empty")
    public static void sourceRodRecipesAndHazardsRemainExact(GameTestHelper helper) {
        helper.assertTrue(BreederRecipes.rodRecipeCount() == 30,
                "Ten source isotope transitions must expand to single, dual, and quad rods");
        helper.assertTrue(BreederRecipes.rod(BreedingRodItem.Type.LITHIUM).flux() == 200
                        && BreederRecipes.rod(BreedingRodItem.Type.TH232).flux() == 500
                        && BreederRecipes.rod(BreedingRodItem.Type.PU238).flux() == 1_000,
                "Source breeding thresholds must remain exact");
        helper.assertTrue(BreedingRodItem.Form.QUAD.breedingFluxMultiplier() == 3
                        && BreedingRodItem.radiation(BreedingRodItem.Type.CO60, BreedingRodItem.Form.SINGLE) == 15F
                        && BreedingRodItem.radiation(BreedingRodItem.Type.CO60, BreedingRodItem.Form.DUAL) == 30F
                        && BreedingRodItem.radiation(BreedingRodItem.Type.CO60, BreedingRodItem.Form.QUAD) == 60F,
                "Quad breeding cost is deliberately x3 while rod radiation uses source x1/x2/x4 material amounts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactFluxBreedsAfterSourceFloatBoundary(GameTestHelper helper) {
        BreedingReactorBlockEntity reactor = place(helper, new BlockPos(2, 2, 2));
        Item rodItem = item("rod");
        reactor.setItem(BreedingReactorBlockEntity.INPUT,
                BreedingRodItem.stack(rodItem, BreedingRodItem.Type.LITHIUM, 1));
        for (int i = 0; i < 399; i++) reactor.testTickWithFlux(200);
        helper.assertTrue(reactor.getItem(BreedingReactorBlockEntity.OUTPUT).isEmpty(),
                "Exact threshold flux must not finish before the source 400th tick");
        reactor.testTickWithFlux(200);
        helper.assertTrue(reactor.getItem(BreedingReactorBlockEntity.OUTPUT).isEmpty(),
                "Source float accumulation must leave exact-threshold breeding just short on tick 400");
        reactor.testTickWithFlux(200);
        helper.assertTrue(reactor.getItem(BreedingReactorBlockEntity.INPUT).isEmpty()
                        && BreedingRodItem.type(reactor.getItem(BreedingReactorBlockEntity.OUTPUT))
                        == BreedingRodItem.Type.TRITIUM && reactor.progress() == 0F,
                "Tick 401 must consume one Lithium Rod and produce one Tritium Rod");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void structureAndSidedInventoryUseSourceContract(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 2, 2);
        BreedingReactorBlockEntity reactor = place(helper, relative);
        helper.assertTrue(helper.getBlockEntity(relative.above()) instanceof BreedingReactorProxyBlockEntity
                        && helper.getBlockEntity(relative.above(2)) instanceof BreedingReactorProxyBlockEntity,
                "Both upper source dummies must proxy the two-slot inventory");
        for (Direction side : Direction.values()) {
            helper.assertTrue(reactor.getSlotsForFace(side).length == 2
                            && reactor.canPlaceItemThroughFace(0, new ItemStack(net.minecraft.world.item.Items.DIRT), side)
                            && !reactor.canPlaceItemThroughFace(1, new ItemStack(net.minecraft.world.item.Items.DIRT), side)
                            && reactor.canTakeItemThroughFace(1, ItemStack.EMPTY, side),
                    "Every side must insert only slot zero and extract only slot one");
        }
        reactor.setItem(0, BreedingRodItem.stack(item("rod_dual"), BreedingRodItem.Type.CO, 1));
        reactor.testTickWithFlux(199);
        helper.assertTrue(reactor.progress() == 0F, "Below-threshold flux must reset progress immediately");
        helper.succeed();
    }

    private static BreedingReactorBlockEntity place(GameTestHelper helper, BlockPos relative) {
        Block block = BuiltInRegistries.BLOCK.get(BreedingReactorBlock.ID);
        BreedingReactorBlock reactor = (BreedingReactorBlock) block;
        BlockState lower = reactor.defaultBlockState().setValue(BreedingReactorBlock.FACING, Direction.NORTH);
        helper.setBlock(relative, lower);
        helper.setBlock(relative.above(), lower.setValue(BreedingReactorBlock.PART, BreedingReactorBlock.Part.MIDDLE));
        helper.setBlock(relative.above(2), lower.setValue(BreedingReactorBlock.PART, BreedingReactorBlock.Part.UPPER));
        return helper.getBlockEntity(relative);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id));
    }
}
