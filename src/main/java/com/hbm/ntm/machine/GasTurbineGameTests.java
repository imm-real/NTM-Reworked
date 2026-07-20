package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.GasTurbineBlock;
import com.hbm.ntm.blockentity.GasTurbineBlockEntity;
import com.hbm.ntm.blockentity.GasTurbineProxyBlockEntity;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.item.DenseWireItem;
import com.hbm.ntm.item.PipeItem;
import com.hbm.ntm.item.ShellItem;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasTurbineGameTests {
    private GasTurbineGameTests() { }

    @GameTest(template = "empty")
    public static void sourceFootprintAndPortsStayWhereBobPutThem(GameTestHelper helper) {
        BlockPos core = new BlockPos(8, 2, 8);
        var parts = GasTurbineBlock.partPositions(core, Direction.SOUTH);
        check(helper, parts.size() == 90 && new HashSet<>(parts).size() == 90,
                "Gas turbine must occupy exactly ten by three by three cells");

        var fluids = GasTurbineBlock.fluidConnections(core, Direction.SOUTH);
        check(helper, fluids.size() == 5,
                "Gas turbine must expose four intake proxies and one steam output proxy");
        check(helper, fluids.stream().filter(c -> c.portType() == GasTurbineBlock.Port.FUEL_LUBE).count() == 2
                        && fluids.stream().filter(c -> c.portType() == GasTurbineBlock.Port.WATER).count() == 2,
                "Both ends need their original fuel/lube and water intakes");
        check(helper, fluids.stream().anyMatch(c -> c.port().equals(core.east(5).above())
                        && c.target().equals(core.east(6).above()) && c.outward() == Direction.EAST
                        && c.portType() == GasTurbineBlock.Port.STEAM),
                "Hot steam output must remain on the east upper face when facing south");

        var power = GasTurbineBlock.powerConnection(core, Direction.SOUTH);
        check(helper, power.port().equals(core.west(4).above())
                        && power.target().equals(core.west(5).above()) && power.outward() == Direction.WEST,
                "HE output must remain opposite the hot steam outlet");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void proxyKeepsFuelAndLubricantOnTheOutsideFace(GameTestHelper helper) {
        BlockPos core = new BlockPos(8, 2, 8);
        GasTurbineBlock block = ModBlocks.MACHINE_TURBINE_GAS.get();
        helper.setBlock(core, coreState(Direction.SOUTH));
        BlockPos port = core.south().west();
        helper.setBlock(port, block.stateForPart(port, core, Direction.SOUTH));

        GasTurbineProxyBlockEntity proxy = helper.getBlockEntity(port);
        IFluidHandler handler = proxy.fluidHandler(Direction.SOUTH);
        check(helper, handler != null && proxy.fluidHandler(Direction.NORTH) == null,
                "Fuel proxy must only connect through its outward face");
        check(helper, handler.fill(new FluidStack(ModFluids.GAS.get(), 250),
                        IFluidHandler.FluidAction.EXECUTE) == 250
                        && handler.fill(new FluidStack(ModFluids.LUBRICANT.get(), 40),
                        IFluidHandler.FluidAction.EXECUTE) == 40
                        && handler.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1),
                        IFluidHandler.FluidAction.EXECUTE) == 0,
                "Shared intake must accept gas and lubricant but tell water to use its own door");
        GasTurbineBlockEntity turbine = helper.getBlockEntity(core);
        check(helper, turbine.fuelAmount() == 250 && turbine.lubricantAmount() == 40,
                "Proxy fills must land in the core tanks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void startupStillTakesTheFullFiveHundredEightyTicks(GameTestHelper helper) {
        BlockPos core = new BlockPos(8, 2, 8);
        helper.setBlock(core, coreState(Direction.SOUTH));
        GasTurbineBlockEntity turbine = helper.getBlockEntity(core);
        IFluidHandler intake = turbine.fluidHandler(GasTurbineBlock.Port.FUEL_LUBE);
        intake.fill(new FluidStack(ModFluids.GAS.get(), 50_000), IFluidHandler.FluidAction.EXECUTE);
        intake.fill(new FluidStack(ModFluids.LUBRICANT.get(), 8_000), IFluidHandler.FluidAction.EXECUTE);
        turbine.setControl(GasTurbineBlockEntity.Control.STATE, 1);

        for (int tick = 0; tick < 579; tick++) tick(helper, core, turbine);
        check(helper, turbine.state() == -1,
                "The turbine must not skip the source startup sequence");
        tick(helper, core, turbine);
        check(helper, turbine.state() == 1 && turbine.rpm() == 10 && turbine.temperature() == 300
                        && turbine.counter() == 225,
                "Tick 580 must finish startup at idle RPM and temperature");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void assemblyRecipeKeepsAllSevenSourceIngredients(GameTestHelper helper) {
        AssemblyRecipe recipe = AssemblyRecipes.byName("ass.gasturbine");
        check(helper, recipe != null && recipe.duration() == 400 && recipe.power() == 100L
                        && recipe.inputs().size() == 7
                        && recipe.output().is(ModItems.MACHINE_TURBINE_GAS_ITEM.get()),
                "ass.gasturbine must remain a seven-lane 400-tick recipe at 100 HE/t");
        if (recipe == null) return;

        check(helper, recipe.inputs().get(0).matches(ShellItem.steel(ModItems.SHELL.get(), 10))
                        && recipe.inputs().get(1).matches(DenseWireItem.create(
                        ModItems.WIRE_DENSE.get(), FoundryMaterial.GOLD, 12))
                        && recipe.inputs().get(2).matches(PipeItem.duraSteel(ModItems.PIPE.get(), 4))
                        && recipe.inputs().get(3).matches(PipeItem.steel(ModItems.PIPE.get(), 4))
                        && recipe.inputs().get(4).matches(new net.minecraft.world.item.ItemStack(
                        ModItems.TURBINE_TUNGSTEN.get()))
                        && recipe.inputs().get(5).matches(new net.minecraft.world.item.ItemStack(
                        ModItems.get("ingot_rubber").get(), 12))
                        && recipe.inputs().get(6).matches(CircuitItem.create(
                        ModItems.CIRCUIT.get(), CircuitItem.CircuitType.BASIC, 3)),
                "Recipe must keep Steel Shells, Dense Gold Wire, both pipe grades, blades, Rubber and Basic Circuits");
        helper.succeed();
    }

    private static BlockState coreState(Direction facing) {
        return ModBlocks.MACHINE_TURBINE_GAS.get().defaultBlockState()
                .setValue(GasTurbineBlock.FACING, facing)
                .setValue(GasTurbineBlock.PART_LENGTH, 1)
                .setValue(GasTurbineBlock.PART_SIDE, 5)
                .setValue(GasTurbineBlock.PART_Y, 0);
    }

    private static void tick(GameTestHelper helper, BlockPos position, GasTurbineBlockEntity turbine) {
        GasTurbineBlockEntity.tick(helper.getLevel(), helper.absolutePos(position),
                helper.getBlockState(position), turbine);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
