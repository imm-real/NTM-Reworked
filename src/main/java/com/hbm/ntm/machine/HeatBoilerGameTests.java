package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.block.HeatBoilerBlock;
import com.hbm.ntm.blockentity.FireboxBlockEntity;
import com.hbm.ntm.blockentity.HeatBoilerBlockEntity;
import com.hbm.ntm.blockentity.HeatBoilerProxyBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeatBoilerGameTests {
    private HeatBoilerGameTests() { }

    @GameTest(template = "empty")
    public static void sourceStructurePortsAndConstructionRecipe(GameTestHelper helper) {
        BlockPos corePos = new BlockPos(4, 1, 4);
        HeatBoilerBlockEntity boiler = placeBoiler(helper, corePos);
        int blocks = 0;
        int ports = 0;
        for (BlockPos part : HeatBoilerBlock.partPositions(boiler.getBlockPos())) {
            if (helper.getLevel().getBlockState(part).is(ModBlocks.MACHINE_BOILER.get())) blocks++;
            if (helper.getLevel().getBlockEntity(part) instanceof HeatBoilerProxyBlockEntity) ports++;
        }
        check(helper, blocks == 36, "The source Boiler must occupy a complete 3x4x3 volume");
        check(helper, ports == 3, "The source Boiler must expose two lower side ports and one top port");
        var recipe = AnvilRecipes.byId(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "anvil/machine_boiler"));
        check(helper, recipe != null && recipe.tierLower() == 2 && recipe.inputs().size() == 3
                        && recipe.inputs().get(0).count() == 4 && recipe.inputs().get(1).count() == 16
                        && recipe.inputs().get(2).count() == 8
                        && recipe.outputs().getFirst().stack().get().is(ModItems.MACHINE_BOILER_ITEM.get()),
                "Tier-2 Anvil Boiler construction must preserve 4 Steel Ingots, 16 Copper Plates and 8 Insulators");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireboxHeatBoilsWaterAtExactSourceRatio(GameTestHelper helper) {
        BlockPos boilerPos = new BlockPos(3, 2, 3);
        HeatBoilerBlockEntity boiler = bareBoiler(helper, boilerPos);
        FireboxBlockEntity firebox = bareFirebox(helper, boilerPos.below());
        setFireboxHeat(helper, firebox, 2_000);
        check(helper, boiler.fillWater(1) == 1, "Boiler must accept one mB of Water");
        tick(helper, boiler);
        check(helper, boiler.inputTank().isEmpty(), "One source Boiler operation must consume one mB Water");
        check(helper, boiler.outputTank().getFluidAmount() == 100
                        && boiler.outputTank().getFluid().is(ModFluids.STEAM.get()),
                "One mB Water must become exactly 100 mB Steam");
        check(helper, boiler.heat() == 0 && firebox.getHeatStored() == 1_800,
                "One operation must consume exactly 200 TU after 0.1 heat diffusion");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidPortsFillWaterAndDrainOnlySteam(GameTestHelper helper) {
        BlockPos corePos = new BlockPos(4, 1, 4);
        HeatBoilerBlockEntity boiler = placeBoiler(helper, corePos);
        BlockPos absoluteCore = boiler.getBlockPos();
        BlockPos eastPort = absoluteCore.east();
        IFluidHandler wrongSide = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                eastPort, Direction.WEST);
        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                eastPort, Direction.EAST);
        check(helper, wrongSide == null && handler != null,
                "A lower Boiler port must expose fluid handling only through its outward face");
        check(helper, handler.fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE) == 1_000
                        && handler.fill(new FluidStack(ModFluids.STEAM.get(), 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 0,
                "Boiler ports must accept Water and reject Steam as an input");
        boiler.outputTank().fill(new FluidStack(ModFluids.STEAM.get(), 500),
                IFluidHandler.FluidAction.EXECUTE);
        FluidStack drained = handler.drain(250, IFluidHandler.FluidAction.EXECUTE);
        check(helper, drained.getAmount() == 250 && drained.is(ModFluids.STEAM.get())
                        && boiler.inputTank().getFluidAmount() == 1_000,
                "Boiler ports must drain Steam without exposing Water as an output");

        check(helper, boiler.configureInput(FluidIdentifierItem.Selection.OIL),
                "The same source proxy must support an Oil-configured Boiler");
        tick(helper, boiler);
        check(helper, handler.fill(new FluidStack(Fluids.WATER, 1),
                        IFluidHandler.FluidAction.EXECUTE) == 0
                        && handler.fill(new FluidStack(ModFluids.OIL.get(), 125),
                        IFluidHandler.FluidAction.EXECUTE) == 125,
                "An Oil-mode proxy must reject Water and accept only Oil as input");
        boiler.outputTank().fill(new FluidStack(ModFluids.HOTOIL.get(), 250),
                IFluidHandler.FluidAction.EXECUTE);
        FluidStack drainedHotOil = handler.drain(125, IFluidHandler.FluidAction.EXECUTE);
        check(helper, drainedHotOil.getAmount() == 125 && drainedHotOil.is(ModFluids.HOTOIL.get())
                        && boiler.inputTank().getFluidAmount() == 125,
                "An Oil-mode proxy must drain Hot Oil without exposing its Oil input");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullSteamTankBurstsAndPersists(GameTestHelper helper) {
        BlockPos boilerPos = new BlockPos(4, 1, 4);
        HeatBoilerBlockEntity boiler = placeBoiler(helper, boilerPos);
        boiler.outputTank().fill(new FluidStack(ModFluids.STEAM.get(), HeatBoilerBlockEntity.STEAM_CAPACITY),
                IFluidHandler.FluidAction.EXECUTE);
        tick(helper, boiler);
        check(helper, boiler.hasExploded(), "A Boiler with no room for one Steam operation must burst");
        check(helper, helper.getBlockState(boilerPos.above(2)).isAir(),
                "A burst Boiler must remove its source upper structure");
        CompoundTag saved = boiler.saveWithoutMetadata(helper.getLevel().registryAccess());
        HeatBoilerBlockEntity loaded = bareBoiler(helper, new BlockPos(6, 1, 3));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.hasExploded()
                        && loaded.outputTank().getFluidAmount() == HeatBoilerBlockEntity.STEAM_CAPACITY,
                "Burst state and both source tanks must persist");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oilIdentifierSelectsExactHotOilStepAndPersists(GameTestHelper helper) {
        BlockPos boilerPos = new BlockPos(3, 2, 3);
        HeatBoilerBlockEntity boiler = bareBoiler(helper, boilerPos);
        FireboxBlockEntity firebox = bareFirebox(helper, boilerPos.below());
        setFireboxHeat(helper, firebox, 100);

        ItemStack identifier = new ItemStack(ModItems.FLUID_IDENTIFIER_MULTI.get());
        FluidIdentifierItem.set(identifier, FluidIdentifierItem.Selection.OIL, true);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, identifier);
        BlockPos absolute = helper.absolutePos(boilerPos);
        helper.getLevel().getBlockState(absolute).useItemOn(identifier, helper.getLevel(), player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));

        IFluidHandler handler = boiler.fluidHandler(null);
        check(helper, boiler.inputSelection() == FluidIdentifierItem.Selection.OIL
                        && handler.fill(new FluidStack(Fluids.WATER, 1),
                        IFluidHandler.FluidAction.EXECUTE) == 0
                        && handler.fill(new FluidStack(ModFluids.OIL.get(), 1),
                        IFluidHandler.FluidAction.EXECUTE) == 1,
                "An Oil identifier must reset the source input mode to Oil and reject Water");
        tick(helper, boiler);
        check(helper, boiler.inputTank().isEmpty()
                        && boiler.outputTank().getFluidAmount() == 1
                        && boiler.outputTank().getFluid().is(ModFluids.HOTOIL.get())
                        && boiler.outputTank().getCapacity() == HeatBoilerBlockEntity.HOTOIL_CAPACITY,
                "Oil mode must consume 1 mB Oil and produce 1 mB Hot Oil in a 16,000 mB tank");
        check(helper, boiler.heat() == 0 && firebox.getHeatStored() == 90,
                "One Oil operation must consume exactly 10 TU after source heat diffusion");

        CompoundTag saved = boiler.saveWithoutMetadata(helper.getLevel().registryAccess());
        HeatBoilerBlockEntity loaded = bareBoiler(helper, new BlockPos(6, 2, 3));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.inputSelection() == FluidIdentifierItem.Selection.OIL
                        && loaded.outputSelection() == FluidIdentifierItem.Selection.HOTOIL
                        && loaded.outputTank().getFluidAmount() == 1
                        && loaded.outputTank().getCapacity() == HeatBoilerBlockEntity.HOTOIL_CAPACITY,
                "Oil mode, Hot Oil contents and dynamic source capacity must persist");
        check(helper, !loaded.configureInput(FluidIdentifierItem.Selection.HOTOIL),
                "The Boiler must reject identifiers for fluids that are not Boiler-heatable inputs");
        ItemStack hotOilTank = UniversalFluidTankItem.create(ModItems.FLUID_TANK_FULL.get(),
                UniversalFluidTankItem.ContainedFluid.HOTOIL, 1);
        check(helper, UniversalFluidTankItem.fluid(hotOilTank)
                        == UniversalFluidTankItem.ContainedFluid.HOTOIL
                        && UniversalFluidTankItem.ContainedFluid.fromFluid(ModFluids.HOTOIL.get())
                        == UniversalFluidTankItem.ContainedFluid.HOTOIL
                        && UniversalFluidTankItem.ContainedFluid.HOTOIL.color() == 0x300900
                        && UniversalFluidTankItem.ContainedFluid.HOTOIL.fluid().isSame(ModFluids.HOTOIL.get())
                        && hotOilTank.getCraftingRemainingItem().is(ModItems.FLUID_TANK_EMPTY.get()),
                "Hot Oil universal tanks must preserve exact fluid identity, color and empty remainder");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullHotOilTankUsesSourceBurstBoundary(GameTestHelper helper) {
        BlockPos boilerPos = new BlockPos(4, 1, 4);
        HeatBoilerBlockEntity boiler = placeBoiler(helper, boilerPos);
        check(helper, boiler.configureInput(FluidIdentifierItem.Selection.OIL),
                "Oil must be a valid Boiler heating mode");
        tick(helper, boiler);
        boiler.outputTank().fill(new FluidStack(ModFluids.HOTOIL.get(), HeatBoilerBlockEntity.HOTOIL_CAPACITY),
                IFluidHandler.FluidAction.EXECUTE);
        tick(helper, boiler);
        check(helper, boiler.hasExploded(),
                "A full 16,000 mB Hot Oil output must trigger the same source burst boundary as Steam");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryAirUsesExactHotBlastHeatingStep(GameTestHelper helper) {
        BlockPos boilerPos = new BlockPos(3, 2, 3);
        HeatBoilerBlockEntity boiler = bareBoiler(helper, boilerPos);
        FireboxBlockEntity firebox = bareFirebox(helper, boilerPos.below());
        setFireboxHeat(helper, firebox, 50);
        check(helper, boiler.configureInput(FluidIdentifierItem.Selection.AIR)
                        && boiler.fillAir(1) == 1,
                "Ordinary Air identifier and fluid must configure the source Boiler input");
        tick(helper, boiler);
        check(helper, boiler.inputTank().isEmpty()
                        && boiler.outputTank().getFluidAmount() == 1
                        && boiler.outputTank().getFluid().is(ModFluids.AIRBLAST.get())
                        && boiler.outputSelection() == FluidIdentifierItem.Selection.AIRBLAST,
                "One mB ordinary Air must become exactly one mB Hot Air Blast");
        check(helper, boiler.heat() == 0 && firebox.getHeatStored() == 45,
                "The source Air heating step must consume exactly 5TU");

        CompoundTag saved = boiler.saveWithoutMetadata(helper.getLevel().registryAccess());
        HeatBoilerBlockEntity loaded = bareBoiler(helper, new BlockPos(6, 2, 3));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.inputSelection() == FluidIdentifierItem.Selection.AIR
                        && loaded.outputSelection() == FluidIdentifierItem.Selection.AIRBLAST
                        && loaded.outputTank().getCapacity() == HeatBoilerBlockEntity.AIRBLAST_CAPACITY,
                "Ordinary Air mode and its 16,000mB Hot Air Blast tank must persist");
        helper.succeed();
    }

    private static HeatBoilerBlockEntity placeBoiler(GameTestHelper helper, BlockPos relativeCore) {
        HeatBoilerBlock block = ModBlocks.MACHINE_BOILER.get();
        var state = block.defaultBlockState().setValue(HeatBoilerBlock.FACING, Direction.NORTH);
        helper.setBlock(relativeCore, state);
        BlockPos absolute = helper.absolutePos(relativeCore);
        block.setPlacedBy(helper.getLevel(), absolute, state, helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.MACHINE_BOILER_ITEM.get()));
        return (HeatBoilerBlockEntity) helper.getLevel().getBlockEntity(absolute);
    }

    private static HeatBoilerBlockEntity bareBoiler(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_BOILER.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static FireboxBlockEntity bareFirebox(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.HEATER_FIREBOX.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void setFireboxHeat(GameTestHelper helper, FireboxBlockEntity firebox, int heat) {
        CompoundTag tag = firebox.saveWithoutMetadata(helper.getLevel().registryAccess());
        tag.putInt("heatEnergy", heat);
        firebox.loadWithComponents(tag, helper.getLevel().registryAccess());
    }

    private static void tick(GameTestHelper helper, HeatBoilerBlockEntity boiler) {
        HeatBoilerBlockEntity.tick(helper.getLevel(), boiler.getBlockPos(), boiler.getBlockState(), boiler);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
