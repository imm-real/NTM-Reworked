package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.blockentity.SteamTurbineBlockEntity;
import com.hbm.ntm.item.BatteryPackItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SteamTurbineGameTests {
    private SteamTurbineGameTests() { }

    @GameTest(template = "empty")
    public static void normalSteamPreservesExactCapRatioAndEfficiency(GameTestHelper helper) {
        SteamTurbineBlockEntity turbine = place(helper, new BlockPos(3, 2, 3));
        int accepted = turbine.fluidHandler(Direction.NORTH).fill(
                new FluidStack(ModFluids.STEAM.get(), 6_000), IFluidHandler.FluidAction.EXECUTE);
        turbine.processForTest();

        check(helper, accepted == 6_000 && turbine.inputTank().isEmpty()
                        && turbine.outputTank().getFluidAmount() == 60
                        && turbine.outputSelection() == FluidIdentifierItem.Selection.SPENTSTEAM,
                "6,000 mB Steam must cool into exactly 60 mB Low-Pressure Steam");
        check(helper, turbine.getPower() == 10_200L && turbine.lastGenerated() == 10_200L
                        && turbine.lastConsumed() == 6_000 && turbine.lastProduced() == 60,
                "Sixty source operations at 200 HE and 85% efficiency must yield exactly 10,200 HE");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void denseGradesPreserveSourceCoolingLadder(GameTestHelper helper) {
        SteamTurbineBlockEntity turbine = place(helper, new BlockPos(3, 2, 3));
        checkGrade(helper, turbine, FluidIdentifierItem.Selection.HOTSTEAM,
                ModFluids.HOTSTEAM.get(), FluidIdentifierItem.Selection.STEAM, 60_000, 10_200L);
        checkGrade(helper, turbine, FluidIdentifierItem.Selection.SUPERHOTSTEAM,
                ModFluids.SUPERHOTSTEAM.get(), FluidIdentifierItem.Selection.HOTSTEAM, 60_000, 91_800L);
        checkGrade(helper, turbine, FluidIdentifierItem.Selection.ULTRAHOTSTEAM,
                ModFluids.ULTRAHOTSTEAM.get(), FluidIdentifierItem.Selection.SUPERHOTSTEAM, 60_000, 612_000L);
        helper.succeed();
    }

    private static void checkGrade(GameTestHelper helper, SteamTurbineBlockEntity turbine,
                                   FluidIdentifierItem.Selection inputSelection,
                                   net.minecraft.world.level.material.Fluid inputFluid,
                                   FluidIdentifierItem.Selection outputSelection,
                                   int outputAmount, long energy) {
        turbine.setPower(0L);
        turbine.setSelectionForTest(inputSelection);
        int filled = turbine.fluidHandler(Direction.UP).fill(new FluidStack(inputFluid, 6_000),
                IFluidHandler.FluidAction.EXECUTE);
        turbine.processForTest();
        check(helper, filled == 6_000 && turbine.inputTank().isEmpty()
                        && turbine.outputSelection() == outputSelection
                        && turbine.outputTank().getFluidAmount() == outputAmount
                        && turbine.getPower() == energy,
                inputSelection.id() + " must retain its source 6,000 mB cooling ratio and HE yield");
    }

    @GameTest(template = "empty")
    public static void outputSpaceAndBatteryOrderingRetainSourceQuirks(GameTestHelper helper) {
        SteamTurbineBlockEntity turbine = place(helper, new BlockPos(3, 2, 3));
        turbine.outputTank().fill(new FluidStack(ModFluids.SPENTSTEAM.get(),
                turbine.outputTank().getCapacity()), IFluidHandler.FluidAction.EXECUTE);
        turbine.fluidHandler(Direction.DOWN).fill(new FluidStack(ModFluids.STEAM.get(), 6_000),
                IFluidHandler.FluidAction.EXECUTE);
        turbine.processForTest();
        check(helper, turbine.inputTank().getFluidAmount() == 6_000 && turbine.getPower() == 0L,
                "A full output tank must atomically block consumption and generation");

        turbine.outputTank().setFluid(FluidStack.EMPTY);
        ItemStack battery = BatteryPackItem.create(ModItems.BATTERY_PACK.get(),
                BatteryPackItem.BatteryType.BATTERY_REDSTONE, false);
        turbine.setItem(SteamTurbineBlockEntity.BATTERY, battery);
        turbine.setPower(2_000L);
        turbine.chargeBatteryForTest();
        turbine.inputTank().setFluid(FluidStack.EMPTY);
        turbine.processForTest();
        check(helper, ((BatteryPackItem) battery.getItem()).getCharge(battery) == 1_000L
                        && turbine.getPower() == 950L,
                "The battery must receive its 1,000 HE charge before the remaining buffer loses five percent");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void selectorContainersAndSixSideInterfacesMatchSource(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        SteamTurbineBlockEntity turbine = place(helper, pos);
        ItemStack identifier = new ItemStack(ModItems.FLUID_IDENTIFIER_MULTI.get());
        FluidIdentifierItem.set(identifier, FluidIdentifierItem.Selection.HOTSTEAM, true);
        turbine.setItem(SteamTurbineBlockEntity.IDENTIFIER_INPUT, identifier);
        SteamTurbineBlockEntity.tick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), turbine);

        check(helper, turbine.inputSelection() == FluidIdentifierItem.Selection.HOTSTEAM
                        && turbine.outputSelection() == FluidIdentifierItem.Selection.STEAM
                        && turbine.getItem(SteamTurbineBlockEntity.IDENTIFIER_INPUT).isEmpty()
                        && turbine.getItem(SteamTurbineBlockEntity.IDENTIFIER_OUTPUT).is(ModItems.FLUID_IDENTIFIER_MULTI.get()),
                "A changed selector must move to the return slot and clear both typed tanks");
        for (Direction direction : Direction.values()) {
            check(helper, turbine.canConnect(direction) && turbine.fluidHandler(direction) != null,
                    "The source turbine must expose HE and typed fluid transfer on " + direction);
        }
        check(helper, turbine.fluidHandler(Direction.EAST).fill(new FluidStack(ModFluids.STEAM.get(), 100),
                        IFluidHandler.FluidAction.EXECUTE) == 0
                        && turbine.fluidHandler(Direction.WEST).fill(new FluidStack(ModFluids.HOTSTEAM.get(), 100),
                        IFluidHandler.FluidAction.EXECUTE) == 100,
                "Dense selection must reject normal Steam and accept Dense Steam on every face");

        int[] top = turbine.getSlotsForFace(Direction.UP);
        int[] bottom = turbine.getSlotsForFace(Direction.DOWN);
        int[] side = turbine.getSlotsForFace(Direction.NORTH);
        check(helper, Arrays.equals(top, new int[]{SteamTurbineBlockEntity.BATTERY})
                        && Arrays.equals(bottom, new int[]{SteamTurbineBlockEntity.OUTPUT_CONTAINER_OUTPUT})
                        && Arrays.equals(side, new int[]{SteamTurbineBlockEntity.BATTERY})
                        && !turbine.canTakeItemThroughFace(SteamTurbineBlockEntity.OUTPUT_CONTAINER_OUTPUT,
                        ItemStack.EMPTY, Direction.DOWN),
                "Sided automation must retain the source's battery-only input and intentionally inert bottom output");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void portableSteamTanksAndConstructionMatchSource(GameTestHelper helper) {
        check(helper, UniversalFluidTankItem.ContainedFluid.fromFluid(ModFluids.STEAM.get())
                        == UniversalFluidTankItem.ContainedFluid.STEAM
                        && UniversalFluidTankItem.ContainedFluid.fromFluid(ModFluids.HOTSTEAM.get())
                        == UniversalFluidTankItem.ContainedFluid.HOTSTEAM
                        && UniversalFluidTankItem.ContainedFluid.fromFluid(ModFluids.SUPERHOTSTEAM.get())
                        == UniversalFluidTankItem.ContainedFluid.SUPERHOTSTEAM
                        && UniversalFluidTankItem.ContainedFluid.fromFluid(ModFluids.ULTRAHOTSTEAM.get())
                        == UniversalFluidTankItem.ContainedFluid.ULTRAHOTSTEAM
                        && UniversalFluidTankItem.ContainedFluid.fromFluid(ModFluids.SPENTSTEAM.get()) == null,
                "All four source Steam grades need universal tanks while Low-Pressure Steam remains containerless");

        AnvilRecipes.Construction recipe = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "anvil/machine_turbine"));
        check(helper, recipe != null && recipe.validForTier(3) && !recipe.validForTier(2)
                        && recipe.inputs().size() == 3
                        && recipe.inputs().get(0).count() == 1
                        && recipe.inputs().get(0).matches(new ItemStack(ModItems.TURBINE_TITANIUM.get()))
                        && recipe.inputs().get(1).count() == 2
                        && recipe.inputs().get(1).matches(new ItemStack(ModItems.COIL_COPPER.get()))
                        && recipe.inputs().get(2).count() == 4
                        && recipe.inputs().get(2).matches(new ItemStack(ModItems.get("ingot_steel").get()))
                        && recipe.outputs().getFirst().stack().get().is(ModItems.MACHINE_TURBINE_ITEM.get()),
                "Tier 3 construction must keep one Titanium Turbine, two Copper Coils and four Steel Ingots");
        helper.succeed();
    }

    private static SteamTurbineBlockEntity place(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_TURBINE.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
