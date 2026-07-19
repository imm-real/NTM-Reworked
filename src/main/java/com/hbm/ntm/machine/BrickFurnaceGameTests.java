package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.StoneResourceBlock;
import com.hbm.ntm.blockentity.BrickFurnaceBlockEntity;
import com.hbm.ntm.item.AshItem;
import com.hbm.ntm.item.StoneResourceBlockItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BrickFurnaceGameTests {
    private BrickFurnaceGameTests() { }

    @GameTest(template = "empty")
    public static void clayUsesExactFourfoldFiftyTickCycle(GameTestHelper helper) {
        BrickFurnaceBlockEntity furnace = bareFurnace(helper, new BlockPos(3, 1, 3));
        furnace.setItem(BrickFurnaceBlockEntity.INPUT, new ItemStack(Items.CLAY_BALL));
        furnace.setItem(BrickFurnaceBlockEntity.FUEL, new ItemStack(Items.STICK));

        tick(helper, furnace);
        check(helper, furnace.burnTime() == 100 && furnace.maxBurnTime() == 100
                        && furnace.progress() == 4 && furnace.getItem(BrickFurnaceBlockEntity.FUEL).isEmpty(),
                "The first Clay tick must load a full 100-tick stick and advance by four");
        for (int tick = 1; tick < 50; tick++) tick(helper, furnace);
        check(helper, furnace.getItem(BrickFurnaceBlockEntity.INPUT).isEmpty()
                        && furnace.getItem(BrickFurnaceBlockEntity.OUTPUT).is(Items.BRICK)
                        && furnace.burnTime() == 51 && furnace.progress() == 0,
                "Clay must smelt into Brick after exactly fifty accelerated ticks without discarding fuel time");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceBurnSpeedTableIsPreserved(GameTestHelper helper) {
        check(helper, BrickFurnaceBlockEntity.burnSpeed(new ItemStack(Items.CLAY_BALL)) == 4
                        && BrickFurnaceBlockEntity.burnSpeed(new ItemStack(ModItems.BALL_FIRECLAY.get())) == 4
                        && BrickFurnaceBlockEntity.burnSpeed(new ItemStack(Items.NETHERRACK)) == 4,
                "Clay Ball, Fireclay and Netherrack must retain the source 4x speed");
        check(helper, BrickFurnaceBlockEntity.burnSpeed(new ItemStack(Items.COBBLESTONE)) == 2
                        && BrickFurnaceBlockEntity.burnSpeed(new ItemStack(Items.SAND)) == 2
                        && BrickFurnaceBlockEntity.burnSpeed(new ItemStack(Items.OAK_LOG)) == 2,
                "Cobblestone, Sand and every modern log must retain the source 2x speed");
        check(helper, BrickFurnaceBlockEntity.burnSpeed(new ItemStack(Items.RAW_IRON)) == 1,
                "All other smelting inputs must remain at 1x speed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lavaLoadsRawFuelTimeOneAshAndBucketRemainder(GameTestHelper helper) {
        BrickFurnaceBlockEntity furnace = bareFurnace(helper, new BlockPos(3, 1, 3));
        furnace.setItem(BrickFurnaceBlockEntity.INPUT, new ItemStack(Items.RAW_IRON));
        furnace.setItem(BrickFurnaceBlockEntity.FUEL, new ItemStack(Items.LAVA_BUCKET));
        tick(helper, furnace);

        check(helper, furnace.burnTime() == 20_000 && furnace.maxBurnTime() == 20_000
                        && furnace.getItem(BrickFurnaceBlockEntity.FUEL).is(Items.BUCKET),
                "A Lava Bucket must load its raw 20,000 furnace ticks and leave one Bucket");
        check(helper, furnace.getItem(BrickFurnaceBlockEntity.ASH_OUTPUT).is(ModItems.POWDER_ASH.get())
                        && AshItem.type(furnace.getItem(BrickFurnaceBlockEntity.ASH_OUTPUT)) == AshItem.AshType.MISC
                        && furnace.ashLevel(AshItem.AshType.MISC) == 18_000,
                "One fuel load must emit only one Misc Ash and retain the source 18,000-point backlog");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sidedAutomationMatchesSourceArrays(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 1, 3);
        BrickFurnaceBlockEntity furnace = bareFurnace(helper, position);
        check(helper, Arrays.equals(furnace.getSlotsForFace(Direction.UP), new int[]{0})
                        && Arrays.equals(furnace.getSlotsForFace(Direction.DOWN), new int[]{2, 1, 3})
                        && Arrays.equals(furnace.getSlotsForFace(Direction.NORTH), new int[]{1}),
                "Top, bottom and side automation arrays must remain {0}, {2,1,3}, and {1}");
        check(helper, helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(position), Direction.UP) != null
                        && helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(position), Direction.DOWN) != null,
                "The Bricked Furnace must expose its sided item capability");
        check(helper, furnace.canPlaceItemThroughFace(BrickFurnaceBlockEntity.INPUT,
                        new ItemStack(Items.DIRT), Direction.UP)
                        && furnace.canPlaceItemThroughFace(BrickFurnaceBlockEntity.FUEL,
                        new ItemStack(Items.COAL), Direction.NORTH)
                        && furnace.canTakeItemThroughFace(BrickFurnaceBlockEntity.OUTPUT,
                        new ItemStack(Items.IRON_INGOT), Direction.DOWN)
                        && !furnace.canTakeItemThroughFace(BrickFurnaceBlockEntity.FUEL,
                        new ItemStack(Items.COAL), Direction.DOWN),
                "Insertion and extraction checks must retain the old ISidedInventory rules");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void machineAndFireclayRecipesKeepExactIdentities(GameTestHelper helper) {
        CraftingInput furnaceInput = CraftingInput.of(3, 3, List.of(
                new ItemStack(Items.BRICK), new ItemStack(Items.BRICK), new ItemStack(Items.BRICK),
                new ItemStack(Items.BRICK), ItemStack.EMPTY, new ItemStack(Items.BRICK),
                new ItemStack(Items.STONE), new ItemStack(Items.STONE), new ItemStack(Items.STONE)));
        var furnaceRecipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                furnaceInput, helper.getLevel()).orElseThrow();
        check(helper, furnaceRecipe.value().assemble(furnaceInput, helper.getLevel().registryAccess())
                        .is(ModItems.MACHINE_FURNACE_BRICK_ITEM.get()),
                "III/I I/BBB must use five vanilla Bricks and three exact Stone blocks");

        CraftingInput fireclayInput = CraftingInput.of(2, 2, List.of(
                new ItemStack(Items.CLAY_BALL), new ItemStack(Items.CLAY_BALL),
                new ItemStack(Items.CLAY_BALL), new ItemStack(ModItems.get("powder_aluminium").get())));
        var fireclayRecipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                fireclayInput, helper.getLevel()).orElseThrow();
        ItemStack fireclay = fireclayRecipe.value().assemble(fireclayInput, helper.getLevel().registryAccess());
        check(helper, fireclay.is(ModItems.BALL_FIRECLAY.get()) && fireclay.getCount() == 4,
                "Three Clay Balls and one Aluminum Dust must make four source Fireclay");

        CraftingInput oreInput = CraftingInput.of(2, 2, List.of(
                new ItemStack(Items.CLAY_BALL), new ItemStack(Items.CLAY_BALL),
                new ItemStack(Items.CLAY_BALL),
                new ItemStack(ModItems.legacyOreBlockItem("ore_aluminium").get())));
        ItemStack oreFireclay = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                        oreInput, helper.getLevel()).orElseThrow().value()
                .assemble(oreInput, helper.getLevel().registryAccess());
        ItemStack limestone = StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(),
                StoneResourceBlock.Type.LIMESTONE, 1);
        CraftingInput limestoneInput = CraftingInput.of(2, 2, List.of(
                new ItemStack(Items.CLAY_BALL), new ItemStack(Items.CLAY_BALL),
                limestone, new ItemStack(Items.SAND)));
        ItemStack limestoneFireclay = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                        limestoneInput, helper.getLevel()).orElseThrow().value()
                .assemble(limestoneInput, helper.getLevel().registryAccess());
        check(helper, oreFireclay.is(ModItems.BALL_FIRECLAY.get()) && oreFireclay.getCount() == 4
                        && limestoneFireclay.is(ModItems.BALL_FIRECLAY.get())
                        && limestoneFireclay.getCount() == 4,
                "Aluminum Ore and Limestone/Sand must preserve both normal-mode source Fireclay routes");
        var smelting = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                new net.minecraft.world.item.crafting.SingleRecipeInput(fireclay), helper.getLevel()).orElseThrow();
        check(helper, smelting.value().assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(fireclay),
                        helper.getLevel().registryAccess()).is(ModItems.get("ingot_firebrick").get()),
                "Fireclay must smelt into the existing exact Firebrick item");
        helper.succeed();
    }

    private static BrickFurnaceBlockEntity bareFurnace(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_FURNACE_BRICK.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void tick(GameTestHelper helper, BrickFurnaceBlockEntity furnace) {
        BrickFurnaceBlockEntity.tick(helper.getLevel(), furnace.getBlockPos(), furnace.getBlockState(), furnace);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
