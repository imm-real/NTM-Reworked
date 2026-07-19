package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.CrackingTowerBlock;
import com.hbm.ntm.blockentity.CrackingTowerBlockEntity;
import com.hbm.ntm.blockentity.CrackingTowerProxyBlockEntity;
import com.hbm.ntm.blockentity.ChemicalPlantBlockEntity;
import com.hbm.ntm.item.ShellItem;
import com.hbm.ntm.item.SourceFluidContainerItem;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.recipe.CrackingRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrackingTowerGameTests {
    private CrackingTowerGameTests() { }

    @GameTest(template = "empty")
    public static void exactIrregularVolumePortsAndConstructionRecipe(GameTestHelper helper) {
        CrackingTowerBlockEntity tower = placeTower(helper, new BlockPos(6, 1, 6));
        Direction facing = tower.getBlockState().getValue(CrackingTowerBlock.FACING);
        int cells = 0;
        int cores = 0;
        int proxies = 0;
        for (BlockPos part : CrackingTowerBlock.partPositions(tower.getBlockPos(), facing)) {
            if (helper.getLevel().getBlockState(part).is(ModBlocks.MACHINE_CATALYTIC_CRACKER.get())) cells++;
            if (helper.getLevel().getBlockEntity(part) instanceof CrackingTowerBlockEntity) cores++;
            if (helper.getLevel().getBlockEntity(part) instanceof CrackingTowerProxyBlockEntity) proxies++;
        }
        check(helper, CrackingTowerBlock.partCount() == 368 && cells == 368 && cores == 1 && proxies == 8,
                "The source tower must occupy exactly 368 cells with one core and eight fluid proxies");

        for (CrackingTowerBlock.Connection connection : CrackingTowerBlock.connections(tower.getBlockPos(), facing)) {
            check(helper, helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                            connection.port(), connection.outward()) != null
                            && helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                            connection.port(), connection.outward().getOpposite()) == null,
                    "Each source port must expose fluid capability only on its outward perimeter face");
        }

        AssemblyRecipe recipe = AssemblyRecipes.byName("ass.crackingtower");
        check(helper, recipe != null && recipe.inputs().size() == 4 && recipe.duration() == 200
                        && recipe.power() == 100 && recipe.output().is(ModItems.MACHINE_CATALYTIC_CRACKER_ITEM.get())
                        && recipe.inputs().get(0).matches(new ItemStack(ModItems.STEEL_SCAFFOLD_ITEM.get(), 16))
                        && recipe.inputs().get(1).matches(ShellItem.steel(ModItems.SHELL.get(), 6))
                        && recipe.inputs().get(2).matches(new ItemStack(ModItems.get("ingot_desh").get(), 12))
                        && recipe.inputs().get(3).matches(new ItemStack(ModItems.get("ingot_niobium").get(), 4)),
                "ass.crackingtower must preserve 16 Scaffolds, 6 Steel Shells, 12 Desh and 4 Niobium");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gasCyclePreservesExactTwicePerFiveTickRatios(GameTestHelper helper) {
        CrackingTowerBlockEntity tower = bareTower(helper, new BlockPos(3, 1, 3));
        tower.configureInput(com.hbm.ntm.item.FluidIdentifierItem.Selection.GAS);
        check(helper, CrackingRecipes.recipeCount() == 1,
                "The Cracking Tower table must contain only the registered Gas route");
        check(helper, tower.fluidHandler().fill(new FluidStack(ModFluids.GAS.get(), 200),
                        IFluidHandler.FluidAction.EXECUTE) == 200
                        && tower.fluidHandler().fill(new FluidStack(ModFluids.STEAM.get(), 400),
                        IFluidHandler.FluidAction.EXECUTE) == 400,
                "The source input ports must route Gas and Steam to their distinct tanks");
        check(helper, tower.processCrackingCycle() == 2,
                "One source five-tick cycle must attempt exactly two operations");
        check(helper, tower.tank(0).isEmpty() && tower.tank(1).isEmpty()
                        && tower.tank(2).getFluidAmount() == 60
                        && tower.tank(2).getFluid().is(ModFluids.PETROLEUM.get())
                        && tower.tank(3).getFluidAmount() == 40
                        && tower.tank(3).getFluid().is(ModFluids.UNSATURATEDS.get())
                        && tower.tank(4).getFluidAmount() == 4
                        && tower.tank(4).getFluid().is(ModFluids.SPENTSTEAM.get()),
                "200 Gas + 400 Steam must yield 60 Petroleum + 40 Unsaturateds + 4 Low-Pressure Steam");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyOutputStallsTheWholeOperationAtomically(GameTestHelper helper) {
        CrackingTowerBlockEntity tower = bareTower(helper, new BlockPos(3, 1, 3));
        tower.configureInput(com.hbm.ntm.item.FluidIdentifierItem.Selection.GAS);
        tower.tank(0).fill(new FluidStack(ModFluids.GAS.get(), 100), IFluidHandler.FluidAction.EXECUTE);
        tower.tank(1).fill(new FluidStack(ModFluids.STEAM.get(), 200), IFluidHandler.FluidAction.EXECUTE);
        tower.tank(3).fill(new FluidStack(ModFluids.UNSATURATEDS.get(),
                CrackingTowerBlockEntity.OUTPUT_CAPACITY - 19), IFluidHandler.FluidAction.EXECUTE);

        check(helper, tower.processCrackingCycle() == 0 && tower.tank(0).getFluidAmount() == 100
                        && tower.tank(1).getFluidAmount() == 200 && tower.tank(2).isEmpty()
                        && tower.tank(3).getFluidAmount() == CrackingTowerBlockEntity.OUTPUT_CAPACITY - 19
                        && tower.tank(4).isEmpty(),
                "Insufficient space in any one output must preserve all three inputs and outputs atomically");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void identifierSwitchAndFiveTankStateRoundTrip(GameTestHelper helper) {
        CrackingTowerBlockEntity tower = bareTower(helper, new BlockPos(3, 1, 3));
        tower.configureInput(com.hbm.ntm.item.FluidIdentifierItem.Selection.GAS);
        tower.tank(0).fill(new FluidStack(ModFluids.GAS.get(), 150), IFluidHandler.FluidAction.EXECUTE);
        tower.tank(1).fill(new FluidStack(ModFluids.STEAM.get(), 400), IFluidHandler.FluidAction.EXECUTE);
        check(helper, tower.processCrackingCycle() == 1 && tower.tank(0).getFluidAmount() == 50,
                "The second source batch attempt must stop when less than 100 mB Gas remains");

        CompoundTag saved = tower.saveWithoutMetadata(helper.getLevel().registryAccess());
        CrackingTowerBlockEntity loaded = bareTower(helper, new BlockPos(6, 1, 3));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.configuredFluid() == com.hbm.ntm.item.FluidIdentifierItem.Selection.GAS
                        && loaded.tank(0).getFluidAmount() == 50 && loaded.tank(1).getFluidAmount() == 200
                        && loaded.tank(2).getFluidAmount() == 30 && loaded.tank(3).getFluidAmount() == 20
                        && loaded.tank(4).getFluidAmount() == 2,
                "The configured identifier and all five source tanks must persist");

        loaded.configureInput(com.hbm.ntm.item.FluidIdentifierItem.Selection.OIL);
        check(helper, loaded.tank(0).isEmpty() && loaded.tank(1).getFluidAmount() == 200
                        && loaded.tank(2).isEmpty() && loaded.tank(3).isEmpty() && loaded.tank(4).isEmpty(),
                "Changing to a currently unsupported cracking input must clear input and recipe outputs, not Steam");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unsaturatedsRetainSourceGasTankIdentityAndColors(GameTestHelper helper) {
        ItemStack empty = new ItemStack(ModItems.GAS_EMPTY.get());
        IFluidHandlerItem handler = empty.getCapability(Capabilities.FluidHandler.ITEM);
        check(helper, handler != null && handler.fill(new FluidStack(ModFluids.UNSATURATEDS.get(), 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 1_000
                        && SourceFluidContainerItem.fluid(handler.getContainer())
                        == SourceFluidContainerItem.ContainedFluid.UNSATURATEDS,
                "Unsaturated Hydrocarbons must fill the source Gas Tank identity at exactly 1,000 mB");
        check(helper, SourceFluidContainerItem.ContainedFluid.UNSATURATEDS.containerColor() == 0x628FAE
                        && SourceFluidContainerItem.ContainedFluid.UNSATURATEDS.labelColor() == 0xEDCF27,
                "Unsaturated Hydrocarbon Gas Tanks must retain source body and label colors");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unsaturatedsUnlockExactNormalRubberOperation(GameTestHelper helper) {
        ChemicalPlantBlockEntity plant = bareChemicalPlant(helper, new BlockPos(3, 1, 3));
        check(helper, plant.selectRecipe(com.hbm.ntm.recipe.ChemicalPlantRecipes.RUBBER),
                "The source chem.rubber operation must be selectable after Cracking Tower completion");
        plant.setPower(20_000L);
        plant.setItem(ChemicalPlantBlockEntity.ITEM_INPUT_START,
                new ItemStack(ModItems.get("sulfur").get()));
        plant.inputTank(0).fill(new FluidStack(ModFluids.UNSATURATEDS.get(), 500),
                IFluidHandler.FluidAction.EXECUTE);
        for (int tick = 0; tick < 100; tick++) {
            ChemicalPlantBlockEntity.tick(helper.getLevel(), plant.getBlockPos(), plant.getBlockState(), plant);
        }
        check(helper, plant.getPower() == 0L && plant.inputTank(0).isEmpty()
                        && plant.getItem(ChemicalPlantBlockEntity.ITEM_INPUT_START).isEmpty()
                        && plant.getItem(ChemicalPlantBlockEntity.ITEM_OUTPUT_START)
                        .is(ModItems.get("ingot_rubber").get())
                        && plant.getItem(ChemicalPlantBlockEntity.ITEM_OUTPUT_START).getCount() == 2,
                "500 mB Unsaturateds plus one Sulfur must make two Rubber Bars in exactly 100x200");
        helper.succeed();
    }

    private static CrackingTowerBlockEntity placeTower(GameTestHelper helper, BlockPos relativeCore) {
        CrackingTowerBlock block = ModBlocks.MACHINE_CATALYTIC_CRACKER.get();
        var state = block.defaultBlockState().setValue(CrackingTowerBlock.FACING, Direction.NORTH);
        helper.setBlock(relativeCore, state);
        BlockPos absoluteCore = helper.absolutePos(relativeCore);
        block.setPlacedBy(helper.getLevel(), absoluteCore, state, helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.MACHINE_CATALYTIC_CRACKER_ITEM.get()));
        return (CrackingTowerBlockEntity) helper.getLevel().getBlockEntity(absoluteCore);
    }

    private static CrackingTowerBlockEntity bareTower(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_CATALYTIC_CRACKER.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static ChemicalPlantBlockEntity bareChemicalPlant(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_CHEMICAL_PLANT.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
