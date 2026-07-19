package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.block.HeatExchangerBlock;
import com.hbm.ntm.blockentity.HeatExchangerBlockEntity;
import com.hbm.ntm.blockentity.HeatExchangerProxyBlockEntity;
import com.hbm.ntm.fluid.FluidTankProperties;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.PipeItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.recipe.HeatExchangerRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeatExchangerGameTests {
    private HeatExchangerGameTests() { }

    @GameTest(template = "empty")
    public static void placementBuildsExactNineCellsAndFourOutwardCornerProxies(GameTestHelper helper) {
        HeatExchangerBlock block = ModBlocks.HEATER_HEATEX.get();
        BlockPos clicked = new BlockPos(4, 1, 4);
        Direction facing = Direction.SOUTH;
        BlockPos core = clicked.relative(facing.getOpposite());
        BlockState placed = block.stateForPart(clicked, core, facing);
        helper.setBlock(clicked, placed);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(clicked), placed,
                helper.makeMockPlayer(GameType.SURVIVAL), new ItemStack(ModItems.HEATER_HEATEX_ITEM.get()));

        int cells = 0;
        int cores = 0;
        int proxies = 0;
        int inert = 0;
        for (BlockPos part : HeatExchangerBlock.partPositions(core)) {
            BlockState state = helper.getBlockState(part);
            check(helper, state.is(block) && HeatExchangerBlock.corePosition(part, state).equals(core),
                    "Every source 3x1x3 Heat Exchanger cell must map to the same core");
            BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(part));
            if (entity instanceof HeatExchangerBlockEntity) cores++;
            else if (entity instanceof HeatExchangerProxyBlockEntity proxy) {
                proxies++;
                Direction outward = state.getValue(HeatExchangerBlock.Z) == 0
                        ? facing.getOpposite() : facing;
                check(helper, proxy.fluidHandler(outward) != null
                                && proxy.fluidHandler(outward.getOpposite()) == null,
                        "Each corner ProxyCombo must connect only through its source front/back face");
            } else inert++;
            cells++;
        }
        check(helper, cells == 9 && cores == 1 && proxies == 4 && inert == 4,
                "The structure must contain one core, four corner proxies and four inert edge cells");

        helper.destroyBlock(core.offset(1, 0, 0));
        for (BlockPos part : HeatExchangerBlock.partPositions(core)) {
            check(helper, !helper.getBlockState(part).is(block),
                    "Breaking any Exchanger part must dismantle all nine cells");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dependencyCompleteCoolingRoutesUseExactSourceRatiosAndHeat(GameTestHelper helper) {
        HeatExchangerRecipes.Cooling coolant = HeatExchangerRecipes.get(FluidIdentifierItem.Selection.COOLANT_HOT);
        HeatExchangerRecipes.Cooling oil = HeatExchangerRecipes.get(FluidIdentifierItem.Selection.HOTOIL);
        HeatExchangerRecipes.Cooling steam = HeatExchangerRecipes.get(FluidIdentifierItem.Selection.STEAM);
        check(helper, coolant.inputAmount() == 1 && coolant.output() == FluidIdentifierItem.Selection.COOLANT
                        && coolant.outputAmount() == 1 && coolant.heatPerOperation() == 300,
                "Hot Coolant must cool 1:1 for 300 TU");
        check(helper, oil.inputAmount() == 1 && oil.output() == FluidIdentifierItem.Selection.OIL
                        && oil.outputAmount() == 1 && oil.heatPerOperation() == 10,
                "Hot Oil must cool 1:1 for 10 TU");
        check(helper, steam.inputAmount() == 100 && steam.output() == FluidIdentifierItem.Selection.SPENTSTEAM
                        && steam.outputAmount() == 1 && steam.heatPerOperation() == 100,
                "Steam must cool 100:1 with the source 50% Heat Exchanger efficiency for 100 TU");

        HeatExchangerBlockEntity exchanger = bareExchanger(helper, new BlockPos(3, 1, 3));
        exchanger.setCycleControls(3, 1);
        exchanger.fluidHandler().fill(new FluidStack(ModFluids.COOLANT_HOT.get(), 5),
                IFluidHandler.FluidAction.EXECUTE);
        exchanger.convertForTest(0);
        check(helper, exchanger.inputTank().getFluidAmount() == 2
                        && exchanger.outputTank().getFluidAmount() == 3 && exchanger.heatEnergy() == 900,
                "A three-operation Coolant cycle must consume three mB, produce three mB and add 900 TU");

        exchanger.selectInput(FluidIdentifierItem.Selection.HOTOIL);
        exchanger.setCycleControls(24_000, 1);
        exchanger.fluidHandler().fill(new FluidStack(ModFluids.HOTOIL.get(), 4),
                IFluidHandler.FluidAction.EXECUTE);
        exchanger.convertForTest(0);
        check(helper, exchanger.outputSelection() == FluidIdentifierItem.Selection.OIL
                        && exchanger.outputTank().getFluidAmount() == 4 && exchanger.heatEnergy() == 940,
                "Changing routes must clear the old output type before exact Hot Oil conversion");

        exchanger.selectInput(FluidIdentifierItem.Selection.STEAM);
        exchanger.fluidHandler().fill(new FluidStack(ModFluids.STEAM.get(), 250),
                IFluidHandler.FluidAction.EXECUTE);
        exchanger.convertForTest(0);
        check(helper, exchanger.inputTank().getFluidAmount() == 50
                        && exchanger.outputSelection() == FluidIdentifierItem.Selection.SPENTSTEAM
                        && exchanger.outputTank().getFluidAmount() == 2 && exchanger.heatEnergy() == 1_140,
                "250 mB Steam must perform two operations, leave 50 mB and produce two mB Low-Pressure Steam");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void denseSteamFamilyAndCoolingLadderMatchSourceTraits(GameTestHelper helper) {
        FluidIdentifierItem.Selection hot = FluidIdentifierItem.Selection.HOTSTEAM;
        FluidIdentifierItem.Selection superhot = FluidIdentifierItem.Selection.SUPERHOTSTEAM;
        FluidIdentifierItem.Selection ultrahot = FluidIdentifierItem.Selection.ULTRAHOTSTEAM;
        check(helper, hot.color() == 0xE7D6D6 && hot.accepts(ModFluids.HOTSTEAM.get())
                        && superhot.color() == 0xE7B7B7 && superhot.accepts(ModFluids.SUPERHOTSTEAM.get())
                        && ultrahot.color() == 0xE39393 && ultrahot.accepts(ModFluids.ULTRAHOTSTEAM.get()),
                "Dense Steam identifiers must retain the exact source colors and fluid identities");
        for (FluidIdentifierItem.Selection selection : new FluidIdentifierItem.Selection[]{hot, superhot, ultrahot}) {
            FluidTankProperties.Profile profile = FluidTankProperties.get(selection);
            check(helper, profile.health() == 4 && profile.flammability() == 0
                            && profile.reactivity() == 0 && profile.gaseous() && !profile.flammable(),
                    "Every Dense Steam grade must retain the source 4/0/0 nonflammable gas profile");
        }
        check(helper, UniversalFluidTankItem.ContainedFluid.fromFluid(ModFluids.HOTSTEAM.get())
                        == UniversalFluidTankItem.ContainedFluid.HOTSTEAM
                        && UniversalFluidTankItem.ContainedFluid.fromFluid(ModFluids.SUPERHOTSTEAM.get())
                        == UniversalFluidTankItem.ContainedFluid.SUPERHOTSTEAM
                        && UniversalFluidTankItem.ContainedFluid.fromFluid(ModFluids.ULTRAHOTSTEAM.get())
                        == UniversalFluidTankItem.ContainedFluid.ULTRAHOTSTEAM,
                "Unsiphonable Dense Steam grades still retain the source universal tanks");

        HeatExchangerRecipes.Cooling hotCooling = HeatExchangerRecipes.get(hot);
        HeatExchangerRecipes.Cooling superhotCooling = HeatExchangerRecipes.get(superhot);
        HeatExchangerRecipes.Cooling ultrahotCooling = HeatExchangerRecipes.get(ultrahot);
        check(helper, hotCooling.inputAmount() == 1 && hotCooling.output() == FluidIdentifierItem.Selection.STEAM
                        && hotCooling.outputAmount() == 10 && hotCooling.heatPerOperation() == 1,
                "One mB Dense Steam must cool into ten mB Steam and recover one TU");
        check(helper, superhotCooling.inputAmount() == 1 && superhotCooling.output() == hot
                        && superhotCooling.outputAmount() == 10 && superhotCooling.heatPerOperation() == 9,
                "One mB Super Dense Steam must cool into ten mB Dense Steam and recover nine TU");
        check(helper, ultrahotCooling.inputAmount() == 1 && ultrahotCooling.output() == superhot
                        && ultrahotCooling.outputAmount() == 10 && ultrahotCooling.heatPerOperation() == 60,
                "One mB Ultra Dense Steam must cool into ten mB Super Dense Steam and recover 60 TU");

        HeatExchangerBlockEntity exchanger = bareExchanger(helper, new BlockPos(3, 1, 3));
        exchanger.selectInput(ultrahot);
        exchanger.fluidHandler().fill(new FluidStack(ModFluids.ULTRAHOTSTEAM.get(), 2),
                IFluidHandler.FluidAction.EXECUTE);
        exchanger.convertForTest(0);
        check(helper, exchanger.outputSelection() == superhot && exchanger.outputTank().getFluidAmount() == 20
                        && exchanger.heatEnergy() == 120,
                "The Heat Exchanger must execute the Ultra-to-Super Dense Steam step");

        exchanger.selectInput(superhot);
        exchanger.fluidHandler().fill(new FluidStack(ModFluids.SUPERHOTSTEAM.get(), 2),
                IFluidHandler.FluidAction.EXECUTE);
        exchanger.convertForTest(0);
        check(helper, exchanger.outputSelection() == hot && exchanger.outputTank().getFluidAmount() == 20
                        && exchanger.heatEnergy() == 138,
                "The Heat Exchanger must execute the Super-to-Dense Steam step");

        exchanger.selectInput(hot);
        exchanger.fluidHandler().fill(new FluidStack(ModFluids.HOTSTEAM.get(), 2),
                IFluidHandler.FluidAction.EXECUTE);
        exchanger.convertForTest(0);
        check(helper, exchanger.outputSelection() == FluidIdentifierItem.Selection.STEAM
                        && exchanger.outputTank().getFluidAmount() == 20 && exchanger.heatEnergy() == 140,
                "The Heat Exchanger must complete the Dense-to-normal Steam step");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void amountCapAndTickDelayMatchSourceCycleControls(GameTestHelper helper) {
        HeatExchangerBlockEntity exchanger = bareExchanger(helper, new BlockPos(3, 1, 3));
        exchanger.selectInput(FluidIdentifierItem.Selection.HOTOIL);
        exchanger.setCycleControls(2, 5);
        exchanger.fluidHandler().fill(new FluidStack(ModFluids.HOTOIL.get(), 10),
                IFluidHandler.FluidAction.EXECUTE);
        exchanger.convertForTest(4);
        check(helper, exchanger.inputTank().getFluidAmount() == 10 && exchanger.heatEnergy() == 0,
                "A cycle must not run before game time is divisible by its delay");
        exchanger.convertForTest(5);
        exchanger.convertForTest(10);
        check(helper, exchanger.inputTank().getFluidAmount() == 6
                        && exchanger.outputTank().getFluidAmount() == 4 && exchanger.heatEnergy() == 40,
                "Each eligible cycle must be capped to the configured number of operations");
        exchanger.setCycleControls(99_999, -2);
        check(helper, exchanger.amountToCool() == 24_000 && exchanger.tickDelay() == 1,
                "Source control packets must clamp amount to tank capacity and delay to at least one tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void identifierResetRememberedTypeAndStatePersistenceMatchSource(GameTestHelper helper) {
        HeatExchangerBlockEntity exchanger = bareExchanger(helper, new BlockPos(3, 1, 3));
        exchanger.fluidHandler().fill(new FluidStack(ModFluids.COOLANT_HOT.get(), 100),
                IFluidHandler.FluidAction.EXECUTE);
        ItemStack invalid = new ItemStack(ModItems.FLUID_IDENTIFIER_MULTI.get());
        FluidIdentifierItem.set(invalid, FluidIdentifierItem.Selection.LUBRICANT, true);
        exchanger.setItem(0, invalid);
        tick(helper, exchanger);
        check(helper, exchanger.selectedInput() == FluidIdentifierItem.Selection.NONE
                        && exchanger.inputTank().isEmpty() && exchanger.outputTank().isEmpty(),
                "A selected fluid without FT_Coolable must reset both source tanks to None");

        exchanger.setItem(0, ItemStack.EMPTY);
        exchanger.selectInput(FluidIdentifierItem.Selection.HOTOIL);
        exchanger.setCycleControls(7, 3);
        exchanger.fluidHandler().fill(new FluidStack(ModFluids.HOTOIL.get(), 11),
                IFluidHandler.FluidAction.EXECUTE);
        exchanger.convertForTest(3);
        exchanger.useUpHeat(20);
        CompoundTag saved = exchanger.saveWithoutMetadata(helper.getLevel().registryAccess());
        HeatExchangerBlockEntity loaded = bareExchanger(helper, new BlockPos(6, 1, 6));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.selectedInput() == FluidIdentifierItem.Selection.HOTOIL
                        && loaded.inputTank().getFluidAmount() == 4 && loaded.outputTank().getFluidAmount() == 7
                        && loaded.heatEnergy() == 50 && loaded.amountToCool() == 7 && loaded.tickDelay() == 3,
                "Removing the identifier must retain type, and tanks/heat/cycle controls must persist");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void transceiverAcceptsOnlyHotInputAndDrainsOnlyColdOutput(GameTestHelper helper) {
        HeatExchangerBlockEntity exchanger = bareExchanger(helper, new BlockPos(3, 1, 3));
        IFluidHandler handler = exchanger.fluidHandler();
        check(helper, handler.fill(new FluidStack(ModFluids.COOLANT.get(), 100),
                        IFluidHandler.FluidAction.EXECUTE) == 0
                        && handler.fill(new FluidStack(ModFluids.COOLANT_HOT.get(), 100),
                        IFluidHandler.FluidAction.EXECUTE) == 100,
                "The shared corner transceiver must accept only the configured hot input type");
        exchanger.setCycleControls(10, 1);
        exchanger.convertForTest(0);
        FluidStack wrong = handler.drain(new FluidStack(ModFluids.COOLANT_HOT.get(), 10),
                IFluidHandler.FluidAction.EXECUTE);
        FluidStack cold = handler.drain(4, IFluidHandler.FluidAction.EXECUTE);
        check(helper, wrong.isEmpty() && cold.is(ModFluids.COOLANT.get()) && cold.getAmount() == 4
                        && exchanger.outputTank().getFluidAmount() == 6,
                "The transceiver must refuse hot-input drain and expose only produced cold output");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tierThreeConstructionUsesExactSourceIdentities(GameTestHelper helper) {
        AnvilRecipes.Construction recipe = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "anvil/heater_heatex"));
        check(helper, recipe != null && recipe.tierLower() == 3 && !recipe.validForTier(2)
                        && recipe.validForTier(3) && recipe.inputs().size() == 4
                        && recipe.icon().is(ModItems.HEATER_HEATEX_ITEM.get()),
                "The Heat Exchanging Heater must remain a four-input Tier 3 operation");
        check(helper, recipe.inputs().get(0).count() == 4
                        && recipe.inputs().get(0).matches(new ItemStack(ModItems.get("ingot_rubber").get()))
                        && recipe.inputs().get(1).count() == 16 && recipe.inputs().get(2).count() == 16
                        && recipe.inputs().get(3).count() == 3
                        && recipe.inputs().get(3).matches(PipeItem.steel(ModItems.PIPE.get(), 1))
                        && !recipe.inputs().get(3).matches(PipeItem.copper(ModItems.PIPE.get(), 1)),
                "Construction must use four Rubber Bars, 16 Copper, 16 Steel Plates and three Steel Pipes");
        helper.succeed();
    }

    private static HeatExchangerBlockEntity bareExchanger(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.HEATER_HEATEX.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void tick(GameTestHelper helper, HeatExchangerBlockEntity exchanger) {
        HeatExchangerBlockEntity.tick(helper.getLevel(), exchanger.getBlockPos(), exchanger.getBlockState(), exchanger);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
