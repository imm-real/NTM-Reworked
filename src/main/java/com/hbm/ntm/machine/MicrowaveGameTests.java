package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.MicrowaveBlockEntity;
import com.hbm.ntm.item.WireFineItem;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
public final class MicrowaveGameTests {
    private MicrowaveGameTests() { }

    @GameTest(template = "empty")
    public static void speedOnePreservesCompletionBoundaryAndExactHe(GameTestHelper helper) {
        MicrowaveBlockEntity microwave = bareMicrowave(helper, new BlockPos(3, 1, 3));
        microwave.setItem(MicrowaveBlockEntity.INPUT, new ItemStack(Items.BEEF));
        microwave.setSpeed(1);
        microwave.setPower(7_550L);
        for (int tick = 0; tick < 150; tick++) tick(helper, microwave);
        check(helper, microwave.time() == 300 && microwave.getPower() == 50L
                        && microwave.getItem(MicrowaveBlockEntity.OUTPUT).isEmpty(),
                "Speed 1 must reach 300 after 150x50 HE without completing on that same tick");
        tick(helper, microwave);
        check(helper, microwave.getItem(MicrowaveBlockEntity.INPUT).isEmpty()
                        && microwave.getItem(MicrowaveBlockEntity.OUTPUT).is(Items.COOKED_BEEF)
                        && microwave.time() == 0 && microwave.getPower() == 50L,
                "The next powered tick must cook the Beef before any further HE draw");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void speedFourPreservesOvershootAndThirtyNinthTickOutput(GameTestHelper helper) {
        MicrowaveBlockEntity microwave = bareMicrowave(helper, new BlockPos(3, 1, 3));
        microwave.setItem(MicrowaveBlockEntity.INPUT, new ItemStack(Items.POTATO));
        microwave.setSpeed(4);
        microwave.setPower(1_950L);
        for (int tick = 0; tick < 38; tick++) tick(helper, microwave);
        check(helper, microwave.time() == 304 && microwave.getPower() == 50L
                        && microwave.getItem(MicrowaveBlockEntity.OUTPUT).isEmpty(),
                "Speed 4 must preserve the source 304 progress overshoot after 38 powered ticks");
        tick(helper, microwave);
        check(helper, microwave.getItem(MicrowaveBlockEntity.OUTPUT).is(Items.BAKED_POTATO)
                        && microwave.time() == 0 && microwave.getPower() == 50L,
                "The overshot cycle must complete on tick 39 without drawing another 50 HE");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void foodGateAndSidedAutomationMatchSource(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 1, 3);
        MicrowaveBlockEntity microwave = bareMicrowave(helper, position);
        microwave.setItem(MicrowaveBlockEntity.INPUT, new ItemStack(Items.RAW_IRON));
        microwave.setSpeed(1);
        microwave.setPower(1_000L);
        check(helper, microwave.isSmeltable(new ItemStack(Items.RAW_IRON)) && !microwave.canProcess(),
                "Automation may insert any smeltable item, but the Microwave must process only food input/output");
        check(helper, Arrays.equals(microwave.getSlotsForFace(Direction.DOWN), new int[]{1})
                        && Arrays.equals(microwave.getSlotsForFace(Direction.UP), new int[]{0})
                        && Arrays.equals(microwave.getSlotsForFace(Direction.NORTH), new int[]{0}),
                "Down must expose output 1 while every other face exposes only input 0");
        check(helper, microwave.canPlaceItemThroughFace(0, new ItemStack(Items.RAW_IRON), Direction.NORTH)
                        && !microwave.canTakeItemThroughFace(0, new ItemStack(Items.RAW_IRON), Direction.NORTH)
                        && microwave.canTakeItemThroughFace(1, new ItemStack(Items.IRON_INGOT), Direction.DOWN)
                        && helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(position), Direction.DOWN) != null,
                "Modern item capability routing must retain the source ISidedInventory rules");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void speedFiveDestroysMachineBeforeDrawingPower(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 1, 3);
        MicrowaveBlockEntity microwave = bareMicrowave(helper, position);
        microwave.setItem(MicrowaveBlockEntity.INPUT, new ItemStack(Items.BEEF));
        microwave.setSpeed(MicrowaveBlockEntity.MAX_SPEED);
        microwave.setPower(MicrowaveBlockEntity.CONSUMPTION);
        tick(helper, microwave);
        check(helper, helper.getBlockState(position).isAir(),
                "A processable speed-5 Microwave must remove itself before consuming its 50 HE");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void progressIsTransientButPowerAndSpeedPersist(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 1, 3);
        MicrowaveBlockEntity microwave = bareMicrowave(helper, position);
        microwave.setPower(12_345L);
        microwave.setSpeed(3);
        microwave.setTimeForTest(217);
        CompoundTag saved = microwave.saveWithoutMetadata(helper.getLevel().registryAccess());
        check(helper, !saved.contains("time") && saved.getLong("power") == 12_345L && saved.getInt("speed") == 3,
                "Source NBT must save only power and speed, not processing time");
        MicrowaveBlockEntity loaded = new MicrowaveBlockEntity(microwave.getBlockPos(), microwave.getBlockState());
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.time() == 0 && loaded.getPower() == 12_345L && loaded.speed() == 3,
                "Reload must reset progress while retaining HE and selected speed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void constructionAndMagnetronAssemblyRecipesMatchSource(GameTestHelper helper) {
        ItemStack polymer = new ItemStack(ModItems.PLATE_POLYMER.get());
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                polymer.copy(), polymer.copy(), polymer.copy(),
                new ItemStack(ModItems.get("plate_steel").get()), new ItemStack(Items.GLASS_PANE),
                new ItemStack(ModItems.MAGNETRON.get()), polymer.copy(), new ItemStack(ModItems.MOTOR.get()),
                polymer.copy()));
        var crafting = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                input, helper.getLevel()).orElseThrow();
        check(helper, crafting.value().assemble(input, helper.getLevel().registryAccess())
                        .is(ModItems.MACHINE_MICROWAVE_ITEM.get()),
                "III/SGM/IDI must retain Insulators, Steel Plate, pane, Magnetron, and Motor");

        AssemblyRecipe magnetron = AssemblyRecipes.get(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "ass.magnetron"));
        ItemStack copper = new ItemStack(ModItems.get("plate_copper").get(), 3);
        ItemStack tungsten = WireFineItem.create(ModItems.WIRE_FINE.get(), WireFineItem.WireMaterial.TUNGSTEN, 4);
        check(helper, magnetron != null && magnetron.duration() == 40 && magnetron.power() == 100L
                        && magnetron.inputs().size() == 2
                        && magnetron.inputs().get(0).matches(copper)
                        && magnetron.inputs().get(1).matches(tungsten)
                        && magnetron.output().is(ModItems.MAGNETRON.get()),
                "ass.magnetron must require 3 Copper Plates and 4 Fine Tungsten Wires for 40x100 HE");
        helper.succeed();
    }

    private static MicrowaveBlockEntity bareMicrowave(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_MICROWAVE.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void tick(GameTestHelper helper, MicrowaveBlockEntity microwave) {
        MicrowaveBlockEntity.tick(helper.getLevel(), microwave.getBlockPos(), microwave.getBlockState(), microwave);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
