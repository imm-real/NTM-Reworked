package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ElectricFurnaceBlock;
import com.hbm.ntm.blockentity.ElectricFurnaceBlockEntity;
import com.hbm.ntm.item.CastPlateItem;
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
public final class ElectricFurnaceGameTests {
    private ElectricFurnaceGameTests() { }

    @GameTest(template = "empty")
    public static void defaultCycleUsesExactHundredTicksAndFiftyHe(GameTestHelper helper) {
        ElectricFurnaceBlockEntity furnace = bareFurnace(helper, new BlockPos(3, 1, 3));
        furnace.setItem(ElectricFurnaceBlockEntity.INPUT, new ItemStack(Items.RAW_IRON));
        furnace.setPower(5_000L);

        tick(helper, furnace);
        check(helper, furnace.progress() == 1 && furnace.getPower() == 4_950L && furnace.lit(),
                "The default furnace must advance one tick, draw exactly 50 HE, and light up");
        for (int tick = 1; tick < ElectricFurnaceBlockEntity.BASE_MAX_PROGRESS; tick++) tick(helper, furnace);

        check(helper, furnace.getItem(ElectricFurnaceBlockEntity.INPUT).isEmpty()
                        && furnace.getItem(ElectricFurnaceBlockEntity.OUTPUT).is(Items.IRON_INGOT)
                        && furnace.getPower() == 0L && furnace.progress() == 0 && !furnace.lit(),
                "One Raw Iron must become one Iron Ingot after exactly 100x50 HE");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void speedAndPowerUpgradesPreserveExactTradeoffs(GameTestHelper helper) {
        ElectricFurnaceBlockEntity speed = bareFurnace(helper, new BlockPos(2, 1, 2));
        speed.setItem(ElectricFurnaceBlockEntity.INPUT, new ItemStack(Items.RAW_IRON));
        speed.setItem(ElectricFurnaceBlockEntity.UPGRADE,
                new ItemStack(ModItems.MACHINE_UPGRADES.get("upgrade_speed_3").get()));
        speed.setPower(1_000L);
        tick(helper, speed);
        check(helper, speed.maxProgress() == 25 && speed.consumption() == 200
                        && speed.progress() == 1 && speed.getPower() == 800L,
                "Speed III must use the exact source 25-tick, 200 HE/t profile");

        ElectricFurnaceBlockEntity saving = bareFurnace(helper, new BlockPos(6, 1, 2));
        saving.setItem(ElectricFurnaceBlockEntity.INPUT, new ItemStack(Items.RAW_IRON));
        saving.setItem(ElectricFurnaceBlockEntity.UPGRADE,
                new ItemStack(ModItems.MACHINE_UPGRADES.get("upgrade_power_3").get()));
        saving.setPower(100L);
        tick(helper, saving);
        check(helper, saving.maxProgress() == 130 && saving.consumption() == 5
                        && saving.progress() == 1 && saving.getPower() == 95L,
                "Power-Saving III must use the exact source 130-tick, 5 HE/t profile");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void brownoutEnforcesExactTwentyTickCooldown(GameTestHelper helper) {
        ElectricFurnaceBlockEntity furnace = bareFurnace(helper, new BlockPos(3, 1, 3));
        furnace.setItem(ElectricFurnaceBlockEntity.INPUT, new ItemStack(Items.RAW_IRON));
        tick(helper, furnace);
        check(helper, furnace.cooldown() == 20 && furnace.progress() == 0,
                "An underpowered tick must reset the source cooldown to twenty");

        furnace.setPower(1_000L);
        for (int tick = 0; tick < 19; tick++) tick(helper, furnace);
        check(helper, furnace.cooldown() == 1 && furnace.progress() == 0 && furnace.getPower() == 1_000L,
                "Restored HE must remain untouched through nineteen cooldown ticks");
        tick(helper, furnace);
        check(helper, furnace.cooldown() == 0 && furnace.progress() == 1 && furnace.getPower() == 950L,
                "The twentieth restored-power tick must resume processing immediately");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void automationExposesOnlyBatteryInputAndOutput(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 1, 3);
        ElectricFurnaceBlockEntity furnace = bareFurnace(helper, position);
        check(helper, Arrays.equals(furnace.getSlotsForFace(Direction.NORTH), new int[]{0, 1, 2}),
                "Every face must expose the exact source slots 0, 1 and 2");
        check(helper, helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(position), Direction.NORTH) != null,
                "The Electric Furnace must expose sided item automation");
        check(helper, furnace.canPlaceItem(ElectricFurnaceBlockEntity.BATTERY,
                        new ItemStack(ModItems.BATTERY_CREATIVE.get()))
                        && furnace.canPlaceItem(ElectricFurnaceBlockEntity.INPUT, new ItemStack(Items.RAW_IRON))
                        && !furnace.canPlaceItem(ElectricFurnaceBlockEntity.OUTPUT, new ItemStack(Items.IRON_INGOT))
                        && furnace.canTakeItemThroughFace(ElectricFurnaceBlockEntity.OUTPUT,
                        new ItemStack(Items.IRON_INGOT), Direction.DOWN),
                "Sided insertion and extraction must match the old ISidedInventory rules");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void constructionRecipeAndLitStateMatchSource(GameTestHelper helper) {
        ItemStack beryllium = new ItemStack(ModItems.get("ingot_beryllium").get());
        ItemStack castCopper = CastPlateItem.create(ModItems.PLATE_CAST.get(),
                CastPlateItem.CastPlateMaterial.COPPER, 1);
        ItemStack coil = new ItemStack(ModItems.COIL_TUNGSTEN.get());
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                beryllium.copy(), beryllium.copy(), beryllium.copy(),
                castCopper.copy(), new ItemStack(Items.FURNACE), castCopper.copy(),
                coil.copy(), coil.copy(), coil.copy()));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input,
                helper.getLevel()).orElseThrow();
        check(helper, recipe.value().assemble(input, helper.getLevel().registryAccess())
                        .is(ModItems.MACHINE_ELECTRIC_FURNACE_ITEM.get()),
                "BBB/WFW/RRR must require Beryllium, exact Cast Copper Plates, Furnace and Tungsten Coils");

        var off = ModBlocks.MACHINE_ELECTRIC_FURNACE.get().defaultBlockState();
        check(helper, off.hasProperty(ElectricFurnaceBlock.FACING)
                        && off.hasProperty(ElectricFurnaceBlock.LIT)
                        && !off.getValue(ElectricFurnaceBlock.LIT),
                "The stable modern block must preserve the source off/on state without replacing its tile");
        helper.succeed();
    }

    private static ElectricFurnaceBlockEntity bareFurnace(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_ELECTRIC_FURNACE.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void tick(GameTestHelper helper, ElectricFurnaceBlockEntity furnace) {
        ElectricFurnaceBlockEntity.tick(helper.getLevel(), furnace.getBlockPos(), furnace.getBlockState(), furnace);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
