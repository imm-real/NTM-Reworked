package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.OilDerrickBlock;
import com.hbm.ntm.blockentity.OilDerrickBlockEntity;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.item.CastPlateItem;
import com.hbm.ntm.item.PipeItem;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFeatures;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.worldgen.OilBubbleFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OilDerrickGameTests {
    private OilDerrickGameTests() { }

    @GameTest(template = "empty")
    public static void oilBubbleAndContainersPreserveSourceIdentities(GameTestHelper helper) {
        check(helper, HbmConfig.OIL_SPAWN_RATE.getDefault() == 100,
                "Oil bubble config must retain source default 100 independently from ordinary ores");
        check(helper, OilBubbleFeature.effectiveFrequency(100, 0.8F, 0.4F) == 100
                        && OilBubbleFeature.effectiveFrequency(100, 2.0F, 0.0F) == 33
                        && OilBubbleFeature.effectiveFrequency(2, 2.0F, 0.0F) == 1,
                "Oil biome weighting must use integer division by three and clamp to one");
        check(helper, OilBubbleFeature.insideBubble(0, 0, 0, 8)
                        && !OilBubbleFeature.insideBubble(6, 0, 0, 8)
                        && !OilBubbleFeature.insideBubble(0, 4, 0, 8),
                "Oil bubbles must use dx²+dz²+3dy² < radius²/2");
        check(helper, BuiltInRegistries.FEATURE.getKey(ModFeatures.OIL_BUBBLE.get())
                        .equals(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "oil_bubble")),
                "The custom Oil bubble feature must be registered under hbm:oil_bubble");

        ItemStack canister = new ItemStack(ModItems.CANISTER_EMPTY.get());
        IFluidHandlerItem oilHandler = canister.getCapability(Capabilities.FluidHandler.ITEM);
        check(helper, oilHandler != null && oilHandler.fill(new FluidStack(ModFluids.OIL.get(), 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 1_000
                        && oilHandler.getContainer().is(ModItems.CANISTER_FULL.get()),
                "An Empty Canister must swap to the exact full Oil canister at 1,000 mB");
        FluidStack drainedOil = oilHandler.drain(1_000, IFluidHandler.FluidAction.EXECUTE);
        check(helper, drainedOil.getAmount() == 1_000 && drainedOil.getFluid().isSame(ModFluids.OIL.get())
                        && oilHandler.getContainer().is(ModItems.CANISTER_EMPTY.get()),
                "A full Oil canister must drain exactly 1,000 mB and return its source empty item");

        ItemStack gasTank = new ItemStack(ModItems.GAS_EMPTY.get());
        IFluidHandlerItem gasHandler = gasTank.getCapability(Capabilities.FluidHandler.ITEM);
        check(helper, gasHandler != null && gasHandler.fill(new FluidStack(ModFluids.GAS.get(), 999),
                        IFluidHandler.FluidAction.SIMULATE) == 0
                        && gasHandler.fill(new FluidStack(ModFluids.GAS.get(), 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 1_000
                        && gasHandler.getContainer().is(ModItems.GAS_FULL.get()),
                "Gas Tanks must reject partial fills and swap only at the exact 1,000 mB boundary");

        for (String recipe : new String[]{"canister_empty", "gas_empty"}) {
            check(helper, helper.getLevel().getRecipeManager().byKey(
                            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, recipe)).isPresent(),
                    "Source empty-container recipe hbm:" + recipe + " must load");
        }

        BlockPos support = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), ModBlocks.OIL_SPILL.get().defaultBlockState(), 3);
        helper.getLevel().destroyBlock(support, false);
        check(helper, helper.getLevel().getBlockState(support.above()).isAir(),
                "Oil Spill must remove itself when its source-style supporting block disappears");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void derrickStructureDrillingAndFiniteExtractionMatchSource(GameTestHelper helper) {
        BlockPos relative = new BlockPos(4, 1, 4);
        OilDerrickBlock block = ModBlocks.MACHINE_WELL.get();
        var state = block.defaultBlockState().setValue(OilDerrickBlock.FACING, Direction.NORTH);
        BlockPos core = helper.absolutePos(relative).atY(20);
        helper.getLevel().setBlock(core, state, 3);
        block.setPlacedBy(helper.getLevel(), core, state, helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.MACHINE_WELL_ITEM.get()));

        int cells = 0;
        for (BlockPos part : OilDerrickBlock.partPositions(core)) {
            check(helper, helper.getLevel().getBlockState(part).is(ModBlocks.MACHINE_WELL.get()),
                    "Every source Derrick dummy cell must resolve to the shared machine block");
            cells++;
        }
        check(helper, cells == 86, "The source Derrick footprint must contain exactly 86 cells");
        check(helper, helper.getLevel().getBlockState(core.above()).is(ModBlocks.MACHINE_WELL.get()),
                "The first MultiblockHandlerXR fill must occupy the center directly above the core");
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            check(helper, helper.getLevel().getBlockState(core.relative(direction)).isAir(),
                    "Ground-level cardinal positions must remain open for HE/fluid connections");
        }
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            check(helper, helper.getLevel().getBlockState(core.offset(x, 0, z)).is(ModBlocks.MACHINE_WELL.get()),
                    "Ground-level corner supports must be present");
        }

        OilDerrickBlockEntity derrick = (OilDerrickBlockEntity) helper.getLevel().getBlockEntity(core);
        helper.getLevel().setBlock(core.below(), ModBlocks.ORE_OIL.get().defaultBlockState(), 3);
        derrick.runDrillCycleForTest(helper.getLevel());
        check(helper, derrick.oilTank().getFluidAmount() == 500
                        && derrick.gasTank().getFluidAmount() >= 100
                        && derrick.gasTank().getFluidAmount() <= 500,
                "One ordinary deposit operation must yield 500 mB Oil and inclusive 100-500 mB Gas; got "
                        + derrick.oilTank().getFluidAmount() + " Oil and "
                        + derrick.gasTank().getFluidAmount() + " Gas");

        helper.getLevel().setBlock(core.below(), Blocks.STONE.defaultBlockState(), 3);
        derrick.runDrillCycleForTest(helper.getLevel());
        check(helper, helper.getLevel().getBlockState(core.below()).is(ModBlocks.OIL_PIPE.get()),
                "A drill cycle must replace one sub-1000-resistance obstruction with hidden oil_pipe");

        helper.getLevel().setBlock(core.below(), ModBlocks.legacy("ore_asbestos").get().defaultBlockState(), 3);
        derrick.runDrillCycleForTest(helper.getLevel());
        check(helper, helper.getLevel().getBlockState(core.below()).is(ModBlocks.OIL_PIPE.get()),
                "Drilled Asbestos Ore must still be replaced by hidden oil_pipe");
        for (int diagonal = -1; diagonal <= 1; diagonal++) {
            check(helper, helper.getLevel().getBlockState(core.offset(diagonal, 10, diagonal))
                            .is(ModBlocks.legacy("gas_asbestos").get()),
                    "Asbestos drilling must preserve the source diagonal gas placement quirk");
        }
        check(helper, helper.getLevel().getBlockState(core.offset(1, 10, 0)).isAir(),
                "The source Derrick coordinate quirk must not be silently broadened to a full 3x3 gas cloud");

        helper.getLevel().setBlock(core.below(), Blocks.BEDROCK.defaultBlockState(), 3);
        derrick.runDrillCycleForTest(helper.getLevel());
        check(helper, helper.getLevel().getBlockState(core.below()).is(Blocks.BEDROCK) && derrick.indicator() == 2,
                "Resistance 1000 or higher must stop drilling and set the blocked indicator");

        BlockPos lower = core.offset(2, 0, 0);
        helper.getLevel().setBlock(lower, ModBlocks.ORE_OIL_EMPTY.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(lower.above(), ModBlocks.ORE_OIL.get().defaultBlockState(), 3);
        check(helper, helper.getLevel().getBlockState(lower).is(ModBlocks.ORE_OIL.get())
                        && helper.getLevel().getBlockState(lower.above()).is(ModBlocks.ORE_OIL_EMPTY.get()),
                "Filled Oil above an empty deposit must preserve the source downward-swap quirk");

        helper.getLevel().setBlock(core, Blocks.AIR.defaultBlockState(), 3);
        for (BlockPos part : OilDerrickBlock.partPositions(core)) {
            check(helper, helper.getLevel().getBlockState(part).isAir(),
                    "Removing the Derrick core must tear down every legacy dummy cell");
        }
        helper.getLevel().setBlock(core.below(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(lower, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(lower.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void derrickRecipeUpgradesAndPersistentDropAreExact(GameTestHelper helper) {
        AssemblyRecipe recipe = AssemblyRecipes.byName("ass.derrick");
        check(helper, recipe != null && recipe.duration() == 200 && recipe.power() == 100
                        && recipe.output().is(ModItems.MACHINE_WELL_ITEM.get()) && recipe.inputs().size() == 5,
                "ass.derrick must be a 200-tick, 100 HE/t operation with five source inputs");
        check(helper, recipe.inputs().get(1).matches(CastPlateItem.create(ModItems.PLATE_CAST.get(),
                        CastPlateItem.CastPlateMaterial.COPPER, 2))
                        && !recipe.inputs().get(1).matches(CastPlateItem.create(ModItems.PLATE_CAST.get(),
                        CastPlateItem.CastPlateMaterial.STEEL, 2)),
                "ass.derrick must require two exact metadata-2900 Cast Copper Plates");
        check(helper, recipe.inputs().get(2).matches(PipeItem.steel(ModItems.PIPE.get(), 4))
                        && !recipe.inputs().get(2).matches(PipeItem.copper(ModItems.PIPE.get(), 4)),
                "ass.derrick must require four exact metadata-30 Steel Pipes");
        check(helper, OilDerrickBlockEntity.effectivePowerRequirement(0, 0, 0) == 100
                        && OilDerrickBlockEntity.effectivePowerRequirement(3, 0, 0) == 175
                        && OilDerrickBlockEntity.effectivePowerRequirement(0, 3, 0) == 25
                        && OilDerrickBlockEntity.effectiveDelay(0, 0, 0) == 50
                        && OilDerrickBlockEntity.effectiveDelay(3, 0, 0) == 14,
                "Derrick Speed and Power upgrades must retain source integer-quarter arithmetic");

        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        var state = ModBlocks.MACHINE_WELL.get().defaultBlockState();
        helper.getLevel().setBlock(pos, state, 3);
        OilDerrickBlockEntity derrick = (OilDerrickBlockEntity) helper.getLevel().getBlockEntity(pos);
        derrick.addFluidsForTest(1_250, 100);
        derrick.setPower(0);
        derrick.burnGasForTest(3);
        check(helper, derrick.gasTank().getFluidAmount() == 70 && derrick.getPower() == 150,
                "Afterburn level 3 must consume up to 30 mB Gas/t and produce 5 HE per mB");
        ItemStack drop = derrick.machineDrop();
        check(helper, drop.is(ModItems.MACHINE_WELL_ITEM.get()),
                "A harvested Derrick must retain the exact machine_well item identity");
        OilDerrickBlockEntity restored = new OilDerrickBlockEntity(pos.above(), state);
        restored.restoreFromItem(drop);
        check(helper, restored.getPower() == 150 && restored.oilTank().getFluidAmount() == 1_250
                        && restored.gasTank().getFluidAmount() == 70,
                "The Derrick item must preserve stored HE, Oil and Gas and restore all three on placement");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void poweredTickUnloadsContainersAndRunsTheSourceCycle(GameTestHelper helper) {
        BlockPos core = helper.absolutePos(new BlockPos(4, 1, 4)).atY(20);
        OilDerrickBlock block = ModBlocks.MACHINE_WELL.get();
        var state = block.defaultBlockState().setValue(OilDerrickBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(core, state, 3);
        block.setPlacedBy(helper.getLevel(), core, state, helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.MACHINE_WELL_ITEM.get()));
        OilDerrickBlockEntity derrick = (OilDerrickBlockEntity) helper.getLevel().getBlockEntity(core);
        derrick.addFluidsForTest(1_000, 1_000);
        derrick.setItem(OilDerrickBlockEntity.OIL_CONTAINER_INPUT, new ItemStack(ModItems.CANISTER_EMPTY.get()));
        derrick.setItem(OilDerrickBlockEntity.GAS_CONTAINER_INPUT, new ItemStack(ModItems.GAS_EMPTY.get()));
        derrick.setPower(10_000);
        helper.getLevel().setBlock(core.below(), ModBlocks.ORE_OIL.get().defaultBlockState(), 3);

        helper.startSequence()
                .thenExecuteAfter(80, () -> {
                    check(helper, derrick.getItem(OilDerrickBlockEntity.OIL_CONTAINER_OUTPUT)
                                    .is(ModItems.CANISTER_FULL.get())
                                    && derrick.getItem(OilDerrickBlockEntity.GAS_CONTAINER_OUTPUT)
                                    .is(ModItems.GAS_FULL.get()),
                            "The normal server tick must unload both 1,000 mB source containers before drilling");
                    check(helper, derrick.getPower() < 10_000
                                    && derrick.oilTank().getFluidAmount() >= 500
                                    && derrick.gasTank().getFluidAmount() >= 100,
                            "A powered normal tick sequence must consume HE and extract ordinary Oil/Gas on its 50-tick boundary");
                })
                .thenExecute(() -> {
                    helper.getLevel().setBlock(core, Blocks.AIR.defaultBlockState(), 3);
                    helper.getLevel().setBlock(core.below(), Blocks.AIR.defaultBlockState(), 3);
                })
                .thenSucceed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
