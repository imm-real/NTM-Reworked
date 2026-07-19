package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.RefineryBlock;
import com.hbm.ntm.blockentity.RefineryBlockEntity;
import com.hbm.ntm.blockentity.RefineryProxyBlockEntity;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.PipeItem;
import com.hbm.ntm.item.ShellItem;
import com.hbm.ntm.item.SourceFluidContainerItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.item.WeldedPlateItem;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RefineryGameTests {
    private RefineryGameTests() { }

    @GameTest(template = "empty")
    public static void completeSourceVolumePortsAndConstructionRecipe(GameTestHelper helper) {
        RefineryBlockEntity refinery = placeRefinery(helper, new BlockPos(4, 1, 4));
        int cells = 0;
        int proxies = 0;
        int outwardFaces = 0;
        for (BlockPos part : RefineryBlock.partPositions(refinery.getBlockPos())) {
            if (helper.getLevel().getBlockState(part).is(ModBlocks.MACHINE_REFINERY.get())) cells++;
            if (helper.getLevel().getBlockEntity(part) instanceof RefineryProxyBlockEntity) {
                proxies++;
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    if (RefineryBlock.canConnectAt(helper.getLevel().getBlockState(part), side)) outwardFaces++;
                }
            }
        }
        check(helper, cells == 81 && proxies == 4 && outwardFaces == 8,
                "The source Refinery must occupy 81 cells with four two-face corner proxies");

        AssemblyRecipe recipe = AssemblyRecipes.byName("ass.refinery");
        check(helper, recipe != null && recipe.inputs().size() == 6 && recipe.duration() == 200
                        && recipe.power() == 100 && recipe.output().is(ModItems.MACHINE_REFINERY_ITEM.get()),
                "ass.refinery must be the exact six-input 200-tick, 100 HE/t construction recipe");
        check(helper, recipe.inputs().get(0).matches(WeldedPlateItem.steel(ModItems.PLATE_WELDED.get(), 3))
                        && recipe.inputs().get(2).matches(ShellItem.steel(ModItems.SHELL.get(), 4))
                        && recipe.inputs().get(3).matches(PipeItem.steel(ModItems.PIPE.get(), 12))
                        && recipe.inputs().get(4).matches(new ItemStack(ModItems.PLATE_POLYMER.get(), 8))
                        && recipe.inputs().get(5).matches(CircuitItem.create(ModItems.CIRCUIT.get(),
                        CircuitItem.CircuitType.ANALOG, 3)),
                "Refinery construction must retain exact Welded Steel, Shell, Pipe, Polymer and Analog identities");
        check(helper, !recipe.inputs().get(0).matches(new ItemStack(ModItems.PLATE_WELDED.get(), 3))
                        && !recipe.inputs().get(3).matches(PipeItem.copper(ModItems.PIPE.get(), 12))
                        && !recipe.inputs().get(5).matches(CircuitItem.create(ModItems.CIRCUIT.get(),
                        CircuitItem.CircuitType.BASIC, 3)),
                "Component-backed Refinery construction inputs must reject default or wrong subtypes");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactTickSplitAndTenthCycleSulfur(GameTestHelper helper) {
        RefineryBlockEntity refinery = bareRefinery(helper, new BlockPos(3, 1, 3));
        refinery.setPower(50L);
        check(helper, refinery.inputFluidHandler().fill(new FluidStack(ModFluids.HOTOIL.get(), 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 1_000,
                "The default source input must accept Hot Oil");
        tick(helper, refinery);
        check(helper, refinery.getPower() == 45L && refinery.inputTank().getFluidAmount() == 900
                        && refinery.outputTank(0).getFluidAmount() == 50
                        && refinery.outputTank(1).getFluidAmount() == 25
                        && refinery.outputTank(2).getFluidAmount() == 15
                        && refinery.outputTank(3).getFluidAmount() == 10,
                "One tick must atomically consume 100 mB + 5 HE and split 50/25/15/10");
        for (int operation = 1; operation < 10; operation++) tick(helper, refinery);
        check(helper, refinery.getPower() == 0L && refinery.inputTank().isEmpty()
                        && refinery.outputTank(0).getFluidAmount() == 500
                        && refinery.outputTank(1).getFluidAmount() == 250
                        && refinery.outputTank(2).getFluidAmount() == 150
                        && refinery.outputTank(3).getFluidAmount() == 100
                        && refinery.getItem(RefineryBlockEntity.SULFUR_OUTPUT).is(ModItems.get("sulfur").get())
                        && refinery.sulfurCounter() == 0,
                "Ten source operations must yield the exact fractions and one Sulfur");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullFractionStallsAtomicallyAndBlockedSulfurIsLost(GameTestHelper helper) {
        RefineryBlockEntity blocked = bareRefinery(helper, new BlockPos(2, 1, 2));
        blocked.setPower(100L);
        blocked.inputFluidHandler().fill(new FluidStack(ModFluids.HOTOIL.get(), 1_000),
                IFluidHandler.FluidAction.EXECUTE);
        blocked.outputTank(2).fill(new FluidStack(ModFluids.LIGHTOIL.get(), RefineryBlockEntity.OUTPUT_CAPACITY),
                IFluidHandler.FluidAction.EXECUTE);
        tick(helper, blocked);
        check(helper, blocked.getPower() == 100L && blocked.inputTank().getFluidAmount() == 1_000
                        && blocked.outputTank(0).isEmpty() && blocked.outputTank(1).isEmpty()
                        && blocked.outputTank(2).getFluidAmount() == RefineryBlockEntity.OUTPUT_CAPACITY
                        && blocked.outputTank(3).isEmpty(),
                "Any one full output must stall every fraction without consuming Hot Oil or HE");

        RefineryBlockEntity lossy = bareRefinery(helper, new BlockPos(6, 1, 3));
        lossy.setPower(50L);
        lossy.inputFluidHandler().fill(new FluidStack(ModFluids.HOTOIL.get(), 1_000),
                IFluidHandler.FluidAction.EXECUTE);
        lossy.setItem(RefineryBlockEntity.SULFUR_OUTPUT, new ItemStack(Blocks.DIRT));
        for (int operation = 0; operation < 10; operation++) tick(helper, lossy);
        check(helper, lossy.inputTank().isEmpty() && lossy.outputTank(0).getFluidAmount() == 500
                        && lossy.getItem(RefineryBlockEntity.SULFUR_OUTPUT).is(Blocks.DIRT.asItem())
                        && lossy.sulfurCounter() == 0,
                "An incompatible Sulfur slot must not stall fluids and must lose the tenth-cycle byproduct");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void identifierSwitchClearsTanksAndStateRoundTrips(GameTestHelper helper) {
        RefineryBlockEntity refinery = bareRefinery(helper, new BlockPos(3, 1, 3));
        refinery.setPower(123L);
        refinery.inputFluidHandler().fill(new FluidStack(ModFluids.HOTOIL.get(), 500),
                IFluidHandler.FluidAction.EXECUTE);
        refinery.outputTank(0).fill(new FluidStack(ModFluids.HEAVYOIL.get(), 250),
                IFluidHandler.FluidAction.EXECUTE);
        ItemStack identifier = new ItemStack(ModItems.FLUID_IDENTIFIER_MULTI.get());
        FluidIdentifierItem.set(identifier, FluidIdentifierItem.Selection.GAS, true);
        refinery.setItem(RefineryBlockEntity.FLUID_IDENTIFIER, identifier);
        tick(helper, refinery);
        check(helper, refinery.configuredFluid() == FluidIdentifierItem.Selection.GAS
                        && refinery.inputTank().isEmpty() && refinery.outputTank(0).isEmpty(),
                "Changing identifier type must empty input and clear outputs when no recipe exists");

        FluidIdentifierItem.set(identifier, FluidIdentifierItem.Selection.HOTOIL, true);
        refinery.setItem(RefineryBlockEntity.FLUID_IDENTIFIER, identifier);
        tick(helper, refinery);
        refinery.inputFluidHandler().fill(new FluidStack(ModFluids.HOTOIL.get(), 250),
                IFluidHandler.FluidAction.EXECUTE);
        CompoundTag saved = refinery.saveWithoutMetadata(helper.getLevel().registryAccess());
        RefineryBlockEntity loaded = new RefineryBlockEntity(BlockPos.ZERO,
                ModBlocks.MACHINE_REFINERY.get().defaultBlockState());
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.getPower() == 123L && loaded.configuredFluid() == FluidIdentifierItem.Selection.HOTOIL
                        && loaded.inputTank().getFluidAmount() == 250
                        && loaded.getItem(RefineryBlockEntity.FLUID_IDENTIFIER)
                        .is(ModItems.FLUID_IDENTIFIER_MULTI.get()),
                "Power, identifier, items and all tank state must survive Refinery NBT");

        ItemStack machineDrop = refinery.machineDrop();
        RefineryBlockEntity replaced = new RefineryBlockEntity(BlockPos.ZERO,
                ModBlocks.MACHINE_REFINERY.get().defaultBlockState());
        replaced.restoreFromItem(machineDrop);
        check(helper, replaced.getPower() == 0L
                        && replaced.configuredFluid() == FluidIdentifierItem.Selection.HOTOIL
                        && replaced.inputTank().getFluidAmount() == 250
                        && replaced.dataAccess().getCount() == 10
                        && replaced.dataAccess().get(9) == FluidIdentifierItem.Selection.HOTOIL.ordinal(),
                "Harvested Refinery items must preserve the five source tanks but not HE or inventory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceContainersPreserveAllFourFractionIdentities(GameTestHelper helper) {
        RefineryBlockEntity refinery = bareRefinery(helper, new BlockPos(3, 1, 3));
        refinery.setItem(RefineryBlockEntity.INPUT_CONTAINER, UniversalFluidTankItem.create(
                ModItems.FLUID_TANK_FULL.get(), UniversalFluidTankItem.ContainedFluid.HOTOIL, 1));
        tick(helper, refinery);
        check(helper, refinery.inputTank().getFluidAmount() == 1_000
                        && refinery.getItem(RefineryBlockEntity.INPUT_REMAINDER)
                        .is(ModItems.FLUID_TANK_EMPTY.get()),
                "A Hot Oil universal tank must load exactly 1,000 mB and return the source empty tank");

        FluidStack[] fluids = {new FluidStack(ModFluids.HEAVYOIL.get(), 1_000),
                new FluidStack(ModFluids.NAPHTHA.get(), 1_000),
                new FluidStack(ModFluids.LIGHTOIL.get(), 1_000),
                new FluidStack(ModFluids.PETROLEUM.get(), 1_000)};
        int[] emptySlots = {RefineryBlockEntity.HEAVY_EMPTY, RefineryBlockEntity.NAPHTHA_EMPTY,
                RefineryBlockEntity.LIGHT_EMPTY, RefineryBlockEntity.PETROLEUM_EMPTY};
        for (int lane = 0; lane < 4; lane++) refinery.outputTank(lane).fill(fluids[lane],
                IFluidHandler.FluidAction.EXECUTE);
        refinery.setItem(emptySlots[0], new ItemStack(ModItems.CANISTER_EMPTY.get()));
        refinery.setItem(emptySlots[1], new ItemStack(ModItems.CANISTER_EMPTY.get()));
        refinery.setItem(emptySlots[2], new ItemStack(ModItems.CANISTER_EMPTY.get()));
        refinery.setItem(emptySlots[3], new ItemStack(ModItems.GAS_EMPTY.get()));
        tick(helper, refinery);
        SourceFluidContainerItem.ContainedFluid[] expected = {
                SourceFluidContainerItem.ContainedFluid.HEAVYOIL,
                SourceFluidContainerItem.ContainedFluid.NAPHTHA,
                SourceFluidContainerItem.ContainedFluid.LIGHTOIL,
                SourceFluidContainerItem.ContainedFluid.PETROLEUM};
        int[] fullSlots = {RefineryBlockEntity.HEAVY_FULL, RefineryBlockEntity.NAPHTHA_FULL,
                RefineryBlockEntity.LIGHT_FULL, RefineryBlockEntity.PETROLEUM_FULL};
        for (int lane = 0; lane < 4; lane++) {
            check(helper, SourceFluidContainerItem.fluid(refinery.getItem(fullSlots[lane])) == expected[lane],
                    "Dedicated Refinery output container lane " + lane + " must retain its exact subtype");
        }
        check(helper, expected[0].containerColor() == 0x513F39 && expected[1].containerColor() == 0x5F6D44
                        && expected[2].containerColor() == 0xB46B52
                        && expected[3].containerColor() == 0x5E7CFF
                        && expected[3].labelColor() == 0xFFE97F,
                "Refinery dedicated containers must retain exact source body and label colors");

        IFluidHandlerItem canister = new ItemStack(ModItems.CANISTER_EMPTY.get())
                .getCapability(Capabilities.FluidHandler.ITEM);
        IFluidHandlerItem gas = new ItemStack(ModItems.GAS_EMPTY.get())
                .getCapability(Capabilities.FluidHandler.ITEM);
        check(helper, canister != null && canister.fill(new FluidStack(ModFluids.NAPHTHA.get(), 1_000),
                        IFluidHandler.FluidAction.SIMULATE) == 1_000
                        && gas != null && gas.fill(new FluidStack(ModFluids.PETROLEUM.get(), 1_000),
                        IFluidHandler.FluidAction.SIMULATE) == 1_000,
                "Empty Canisters and Gas Tanks must advertise the exact new accepted fractions");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyFourCornerOutwardFacesExposeCapabilities(GameTestHelper helper) {
        RefineryBlockEntity refinery = placeRefinery(helper, new BlockPos(4, 1, 4));
        int fluidFaces = 0;
        int itemFaces = 0;
        for (BlockPos part : RefineryBlock.partPositions(refinery.getBlockPos())) {
            if (!(helper.getLevel().getBlockEntity(part) instanceof RefineryProxyBlockEntity)) continue;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                boolean outward = RefineryBlock.canConnectAt(helper.getLevel().getBlockState(part), side);
                var fluid = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, part, side);
                var item = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, part, side);
                check(helper, outward == (fluid != null) && outward == (item != null),
                        "Each Refinery corner must expose capabilities only on its two outward faces");
                if (fluid != null) fluidFaces++;
                if (item != null) itemFaces++;
            }
        }
        check(helper, fluidFaces == 8 && itemFaces == 8,
                "Four source ProxyCombo corners must expose exactly eight fluid/item contacts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingAnyDummyTearsDownAllEightyOneCells(GameTestHelper helper) {
        RefineryBlockEntity refinery = placeRefinery(helper, new BlockPos(4, 1, 4));
        BlockPos topCorner = refinery.getBlockPos().offset(1, 8, 1);
        helper.getLevel().setBlock(topCorner, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (BlockPos part : RefineryBlock.partPositions(refinery.getBlockPos())) {
            check(helper, helper.getLevel().getBlockState(part).isAir(),
                    "Removing an arbitrary Refinery dummy must tear down the full source volume");
        }
        helper.succeed();
    }

    private static RefineryBlockEntity placeRefinery(GameTestHelper helper, BlockPos relativeCore) {
        BlockPos core = helper.absolutePos(relativeCore);
        RefineryBlock block = ModBlocks.MACHINE_REFINERY.get();
        var state = block.defaultBlockState().setValue(RefineryBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(core, state, Block.UPDATE_ALL);
        block.setPlacedBy(helper.getLevel(), core, state, helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.MACHINE_REFINERY_ITEM.get()));
        return (RefineryBlockEntity) helper.getLevel().getBlockEntity(core);
    }

    private static RefineryBlockEntity bareRefinery(GameTestHelper helper, BlockPos relativePosition) {
        BlockPos position = helper.absolutePos(relativePosition);
        helper.getLevel().setBlock(position, ModBlocks.MACHINE_REFINERY.get().defaultBlockState(), Block.UPDATE_ALL);
        return (RefineryBlockEntity) helper.getLevel().getBlockEntity(position);
    }

    private static void tick(GameTestHelper helper, RefineryBlockEntity refinery) {
        RefineryBlockEntity.tick(helper.getLevel(), refinery.getBlockPos(), refinery.getBlockState(), refinery);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
