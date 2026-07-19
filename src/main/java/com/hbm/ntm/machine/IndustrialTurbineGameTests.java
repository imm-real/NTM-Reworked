package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.IndustrialTurbineBlock;
import com.hbm.ntm.blockentity.IndustrialTurbineBlockEntity;
import com.hbm.ntm.blockentity.IndustrialTurbineProxyBlockEntity;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.item.DenseWireItem;
import com.hbm.ntm.item.PipeItem;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IndustrialTurbineGameTests {
    private IndustrialTurbineGameTests() { }

    @GameTest(template = "empty")
    public static void exactStructureAndExternalConnectionsMatchSource(GameTestHelper helper) {
        BlockPos core = new BlockPos(8, 2, 8);
        var parts = IndustrialTurbineBlock.partPositions(core, Direction.SOUTH);
        check(helper, parts.size() == 63 && new HashSet<>(parts).size() == 63,
                "Industrial Turbine must occupy exactly seven by three by three cells");

        var fluids = IndustrialTurbineBlock.fluidConnections(core, Direction.SOUTH);
        check(helper, fluids.size() == 6, "Industrial Turbine must expose exactly six source fluid ports");
        check(helper, fluids.stream().filter(connection -> connection.outward() == Direction.UP).count() == 2,
                "Two source fluid ports must point upward from the roof");
        check(helper, fluids.stream().filter(connection -> connection.outward() == Direction.WEST).count() == 2
                        && fluids.stream().filter(connection -> connection.outward() == Direction.EAST).count() == 2,
                "Four source fluid ports must leave through the two side faces");

        var power = IndustrialTurbineBlock.powerConnection(core, Direction.SOUTH);
        check(helper, power.port().equals(core.north(3).above())
                        && power.target().equals(core.north(4).above())
                        && power.outward() == Direction.NORTH,
                "The HE output must remain one block beyond the rear-center proxy");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void steamBatchPreservesTwentyPercentFlywheelMath(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 2, 3);
        IndustrialTurbineBlockEntity turbine = placeTurbine(helper, position);
        int filled = turbine.portFluidHandler().fill(new FluidStack(ModFluids.STEAM.get(), 750_000),
                IFluidHandler.FluidAction.EXECUTE);
        tick(helper, position, turbine);

        check(helper, filled == 750_000 && turbine.inputTank().getFluidAmount() == 600_000,
                "The source turbine must consume exactly 20% of a full Steam tank per tick");
        check(helper, turbine.outputTank().getFluidAmount() == 1_500
                        && turbine.outputTank().getFluid().is(ModFluids.SPENTSTEAM.get()),
                "150,000 mB Steam must cool into exactly 1,500 mB Spent Steam");
        check(helper, turbine.lastPowerTarget() == 15_000L && turbine.getPower() == 15_000L
                        && turbine.flywheelEnergy() == 285_000L
                        && Math.abs(turbine.spin() - 0.006D) < 0.0000001D,
                "First full Steam tick must generate 300,000 HE, output 15,000 HE and retain 285,000 HE");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compressorLeverCyclesExactGradesAndClearsTanks(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 2, 3);
        IndustrialTurbineBlockEntity turbine = placeTurbine(helper, position);
        turbine.portFluidHandler().fill(new FluidStack(ModFluids.STEAM.get(), 1_000),
                IFluidHandler.FluidAction.EXECUTE);

        turbine.cycleSteamGrade();
        check(helper, turbine.grade() == IndustrialTurbineBlockEntity.SteamGrade.DENSE
                        && turbine.inputTank().getCapacity() == 75_000
                        && turbine.outputTank().getCapacity() == 300_000
                        && turbine.inputTank().isEmpty() && turbine.outputTank().isEmpty(),
                "Dense Steam selection must divide both source tanks by ten and clear their contents");
        turbine.cycleSteamGrade();
        check(helper, turbine.grade() == IndustrialTurbineBlockEntity.SteamGrade.SUPER_DENSE
                        && turbine.inputTank().getCapacity() == 7_500
                        && turbine.outputTank().getCapacity() == 30_000,
                "Super Dense Steam selection must divide both source capacities by one hundred");
        turbine.cycleSteamGrade();
        check(helper, turbine.grade() == IndustrialTurbineBlockEntity.SteamGrade.ULTRA_DENSE
                        && turbine.inputTank().getCapacity() == 750
                        && turbine.outputTank().getCapacity() == 3_000,
                "Ultra Dense Steam selection must divide both source capacities by one thousand");
        turbine.cycleSteamGrade();
        check(helper, turbine.grade() == IndustrialTurbineBlockEntity.SteamGrade.STEAM
                        && turbine.inputTank().getCapacity() == 750_000
                        && turbine.outputTank().getCapacity() == 3_000_000,
                "Fourth lever pull must restore the original Steam capacities");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void proxyFluidCapabilityIsOutwardOnlyAndGradeTyped(GameTestHelper helper) {
        BlockPos core = new BlockPos(4, 2, 4);
        helper.setBlock(core, coreState(Direction.SOUTH));
        BlockPos port = core.south(3).west();
        helper.setBlock(port, coreState(Direction.SOUTH)
                .setValue(IndustrialTurbineBlock.PART_LENGTH, 6)
                .setValue(IndustrialTurbineBlock.PART_SIDE, 2));
        IndustrialTurbineProxyBlockEntity proxy = helper.getBlockEntity(port);
        IFluidHandler handler = proxy.fluidHandler(Direction.WEST);
        check(helper, handler != null && proxy.fluidHandler(Direction.EAST) == null,
                "A side proxy must expose its shared tanks only through the outward source face");
        check(helper, handler.fill(new FluidStack(ModFluids.STEAM.get(), 100),
                        IFluidHandler.FluidAction.EXECUTE) == 100
                        && handler.fill(new FluidStack(ModFluids.HOTSTEAM.get(), 1),
                        IFluidHandler.FluidAction.EXECUTE) == 0,
                "Steam grade must accept Steam and reject Dense Steam");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void assemblyRecipeAndSubtypeDependenciesMatchLegacy(GameTestHelper helper) {
        AssemblyRecipe recipe = AssemblyRecipes.byName("ass.iturbine");
        check(helper, recipe != null && recipe.duration() == 200 && recipe.power() == 100L
                        && recipe.inputs().size() == 6
                        && recipe.output().is(ModItems.MACHINE_INDUSTRIAL_TURBINE_ITEM.get()),
                "ass.iturbine must remain a six-lane 200-tick recipe at 100 HE/t");
        if (recipe == null) return;

        check(helper, recipe.inputs().get(0).count() == 16
                        && recipe.inputs().get(0).matches(new ItemStack(
                        ModItems.get("plate_steel").get(), 16))
                        && recipe.inputs().get(1).count() == 4
                        && recipe.inputs().get(1).matches(new ItemStack(
                        ModItems.get("ingot_rubber").get(), 4))
                        && recipe.inputs().get(2).matches(new ItemStack(ModItems.TURBINE_TITANIUM.get(), 2))
                        && recipe.inputs().get(3).matches(DenseWireItem.create(
                        ModItems.WIRE_DENSE.get(), FoundryMaterial.GOLD, 4))
                        && recipe.inputs().get(4).matches(PipeItem.duraSteel(ModItems.PIPE.get(), 4))
                        && !recipe.inputs().get(4).matches(PipeItem.steel(ModItems.PIPE.get(), 4))
                        && recipe.inputs().get(5).matches(CircuitItem.create(
                        ModItems.CIRCUIT.get(), CircuitItem.CircuitType.BASIC, 2)),
                "Recipe must keep 16 Steel Plates, four Rubber, two rotors, four Dense Gold Wires, four Dura Pipes and two Basic Circuits");
        helper.succeed();
    }

    private static IndustrialTurbineBlockEntity placeTurbine(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, coreState(Direction.SOUTH));
        return helper.getBlockEntity(position);
    }

    private static BlockState coreState(Direction facing) {
        return ModBlocks.MACHINE_INDUSTRIAL_TURBINE.get().defaultBlockState()
                .setValue(IndustrialTurbineBlock.FACING, facing)
                .setValue(IndustrialTurbineBlock.PART_LENGTH, 3)
                .setValue(IndustrialTurbineBlock.PART_SIDE, 1)
                .setValue(IndustrialTurbineBlock.PART_Y, 0);
    }

    private static void tick(GameTestHelper helper, BlockPos position,
                             IndustrialTurbineBlockEntity turbine) {
        IndustrialTurbineBlockEntity.tick(helper.getLevel(), helper.absolutePos(position),
                helper.getBlockState(position), turbine);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
