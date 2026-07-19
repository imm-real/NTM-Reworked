package com.hbm.ntm.block;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.item.ScaffoldBlockItem;
import com.hbm.ntm.item.ShellItem;
import com.hbm.ntm.recipe.ShredderRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScaffoldGameTests {
    private ScaffoldGameTests() { }

    @GameTest(template = "empty")
    public static void steelShellUsesFourPlatesAndRetainsFourIngotsOfMaterial(GameTestHelper helper) {
        var recipe = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "anvil/shell_steel"));
        ItemStack output = recipe == null ? ItemStack.EMPTY : recipe.outputs().getFirst().stack().get();
        CustomModelData model = output.get(DataComponents.CUSTOM_MODEL_DATA);
        check(helper, recipe != null && recipe.tierLower() == 1 && recipe.inputs().size() == 1
                        && recipe.inputs().getFirst().count() == 4 && ShellItem.isSteel(output)
                        && model != null && model.value() == 30,
                "Tier-1 Anvil must form metadata-30 Steel Shell from exactly four Steel Plates");

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(ModItems.get("plate_steel").get(), 4));
        check(helper, AnvilRecipes.canCraft(player.getInventory(), recipe)
                        && AnvilRecipes.craft(player, recipe, false),
                "Four Steel Plates must complete the Steel Shell construction");
        int shells = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.SHELL.get()) && ShellItem.isSteel(stack)) shells += stack.getCount();
        }
        FoundryMaterial.MaterialAmount material = FoundryMaterial.fromItem(output);
        check(helper, shells == 1 && player.getInventory().countItem(ModItems.get("plate_steel").get()) == 0
                        && material != null && material.material() == FoundryMaterial.STEEL
                        && material.amount() == FoundryMaterial.SHELL,
                "Steel Shell construction must consume four plates and remelt to exactly four Steel Ingots");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scaffoldRecipesPreserveAllFourSourceVariants(GameTestHelper helper) {
        ItemStack base = recipeResult(helper, "steel_scaffold");
        check(helper, base.is(ModItems.STEEL_SCAFFOLD_ITEM.get()) && base.getCount() == 8
                        && ScaffoldBlockItem.variant(base) == ScaffoldBlock.Variant.STEEL,
                "Seven Steel Ingots in the source pattern must produce eight base Steel Scaffolds");

        checkVariant(helper, "steel_scaffold_gray", ScaffoldBlock.Variant.STEEL, 0);
        checkVariant(helper, "steel_scaffold_red", ScaffoldBlock.Variant.RED, 1);
        checkVariant(helper, "steel_scaffold_white", ScaffoldBlock.Variant.WHITE, 2);
        checkVariant(helper, "steel_scaffold_yellow", ScaffoldBlock.Variant.YELLOW, 3);

        ItemStack shredded = ShredderRecipes.getResult(base.copyWithCount(1));
        check(helper, shredded.is(ModItems.get("powder_steel_tiny").get()) && shredded.getCount() == 4,
                "Every scaffold color must shred into four Tiny Piles of Steel Powder");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scaffoldPlacementBoundsAndDropsMatchSourceMetadata(GameTestHelper helper) {
        check(helper, ScaffoldBlock.orientationFor(Direction.UP, Direction.NORTH) == 0
                        && ScaffoldBlock.orientationFor(Direction.DOWN, Direction.EAST) == 2
                        && ScaffoldBlock.orientationFor(Direction.NORTH, Direction.SOUTH) == 1
                        && ScaffoldBlock.orientationFor(Direction.WEST, Direction.NORTH) == 3,
                "Scaffold placement must preserve the four source orientation groups");

        ScaffoldBlock block = ModBlocks.STEEL_SCAFFOLD.get();
        AABB verticalZ = block.defaultBlockState().setValue(ScaffoldBlock.ORIENTATION, 0)
                .getShape(helper.getLevel(), BlockPos.ZERO).bounds();
        AABB horizontal = block.defaultBlockState().setValue(ScaffoldBlock.ORIENTATION, 1)
                .getShape(helper.getLevel(), BlockPos.ZERO).bounds();
        AABB verticalX = block.defaultBlockState().setValue(ScaffoldBlock.ORIENTATION, 2)
                .getShape(helper.getLevel(), BlockPos.ZERO).bounds();
        check(helper, verticalZ.minZ == 0.125D && verticalZ.maxZ == 0.875D
                        && horizontal.minY == 0.125D && horizontal.maxY == 0.875D
                        && verticalX.minX == 0.125D && verticalX.maxX == 0.875D,
                "Scaffold selection and collision bounds must retain the source two-sixteenths inset");

        BlockPos position = helper.absolutePos(new BlockPos(2, 1, 2));
        var yellow = block.defaultBlockState().setValue(ScaffoldBlock.VARIANT, ScaffoldBlock.Variant.YELLOW)
                .setValue(ScaffoldBlock.ORIENTATION, 3);
        helper.getLevel().setBlock(position, yellow, Block.UPDATE_ALL);
        var drops = Block.getDrops(yellow, helper.getLevel(), position, null);
        check(helper, drops.size() == 1 && drops.getFirst().getCount() == 1
                        && ScaffoldBlockItem.variant(drops.getFirst()) == ScaffoldBlock.Variant.YELLOW
                        && drops.getFirst().getOrDefault(DataComponents.CUSTOM_MODEL_DATA,
                        new CustomModelData(-1)).value() == 3,
                "Breaking a colored scaffold must preserve its low source metadata variant");
        helper.succeed();
    }

    private static void checkVariant(GameTestHelper helper, String recipeId, ScaffoldBlock.Variant variant,
                                     int metadata) {
        ItemStack result = recipeResult(helper, recipeId);
        CustomModelData model = result.get(DataComponents.CUSTOM_MODEL_DATA);
        check(helper, result.is(ModItems.STEEL_SCAFFOLD_ITEM.get()) && result.getCount() == 8
                        && ScaffoldBlockItem.variant(result) == variant
                        && model != null && model.value() == metadata,
                recipeId + " must output eight scaffolds with source metadata " + metadata);
    }

    private static ItemStack recipeResult(GameTestHelper helper, String id) {
        return helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id))
                .map(holder -> holder.value().getResultItem(helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
