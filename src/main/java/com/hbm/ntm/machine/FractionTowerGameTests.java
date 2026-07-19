package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.FractionTowerBlock;
import com.hbm.ntm.block.SteelGrateBlock;
import com.hbm.ntm.block.FractionTowerSeparatorBlock;
import com.hbm.ntm.blockentity.FractionTowerBlockEntity;
import com.hbm.ntm.blockentity.FractionTowerProxyBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.SourceFluidContainerItem;
import com.hbm.ntm.item.ShellItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.item.WeldedPlateItem;
import com.hbm.ntm.recipe.FractionRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FractionTowerGameTests {
    private FractionTowerGameTests() { }

    @GameTest(template = "empty")
    public static void exactSegmentVolumePortsAndConstructionChain(GameTestHelper helper) {
        FractionTowerBlockEntity tower = placeTower(helper, new BlockPos(5, 1, 5));
        int cells = 0;
        int cores = 0;
        int proxies = 0;
        for (BlockPos part : FractionTowerBlock.partPositions(tower.getBlockPos())) {
            if (helper.getLevel().getBlockState(part).is(ModBlocks.MACHINE_FRACTION_TOWER.get())) cells++;
            if (helper.getLevel().getBlockEntity(part) instanceof FractionTowerBlockEntity) cores++;
            if (helper.getLevel().getBlockEntity(part) instanceof FractionTowerProxyBlockEntity) proxies++;
        }
        check(helper, cells == 27 && cores == 1 && proxies == 4,
                "One source segment must be a full 3x3x3 volume with one core and four fluid proxies");
        for (FractionTowerBlock.Connection connection : FractionTowerBlock.connections(tower.getBlockPos())) {
            check(helper, helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                            connection.port(), connection.outward()) != null
                            && helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                            connection.port(), connection.outward().getOpposite()) == null,
                    "Each fraction port must expose capability only on its outward face");
        }

        var manager = helper.getLevel().getRecipeManager();
        ItemStack steel = new ItemStack(ModItems.get("ingot_steel").get());
        ItemStack beam = manager.getRecipeFor(RecipeType.CRAFTING,
                        CraftingInput.of(1, 3, List.of(steel.copy(), steel.copy(), steel.copy())), helper.getLevel())
                .orElseThrow().value().assemble(CraftingInput.of(1, 3,
                        List.of(steel.copy(), steel.copy(), steel.copy())), helper.getLevel().registryAccess());
        check(helper, beam.is(ModItems.STEEL_BEAM_ITEM.get()) && beam.getCount() == 8,
                "Three vertical Steel Ingots must make eight source Steel Beams");
        ItemStack grate = manager.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(2, 2, List.of(
                        beam.copyWithCount(1), beam.copyWithCount(1), beam.copyWithCount(1), beam.copyWithCount(1))),
                        helper.getLevel()).orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        check(helper, grate.is(ModItems.STEEL_GRATE_ITEM.get()) && grate.getCount() == 4,
                "Four Steel Beams must make four source Steel Grates");
        ItemStack welded = WeldedPlateItem.steel(ModItems.PLATE_WELDED.get(), 1);
        CraftingInput towerInput = CraftingInput.of(1, 3,
                List.of(welded.copy(), grate.copyWithCount(1), welded.copy()));
        ItemStack result = manager.getRecipeFor(RecipeType.CRAFTING, towerInput, helper.getLevel())
                .orElseThrow().value().assemble(towerInput, helper.getLevel().registryAccess());
        check(helper, result.is(ModItems.MACHINE_FRACTION_TOWER_ITEM.get()) && result.getCount() == 1,
                "Welded Plate, Steel Grate, Welded Plate must make one Fractioning Tower");
        check(helper, manager.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(1, 3, List.of(
                new ItemStack(ModItems.PLATE_WELDED.get()), grate.copyWithCount(1), welded.copy())),
                helper.getLevel()).isEmpty(), "The tower recipe must require the exact welded-Steel component");

        CraftingInput separatorInput = CraftingInput.of(3, 1, List.of(new ItemStack(Blocks.IRON_BARS),
                ShellItem.steel(ModItems.SHELL.get(), 1), new ItemStack(Blocks.IRON_BARS)));
        ItemStack separatorResult = manager.getRecipeFor(RecipeType.CRAFTING, separatorInput, helper.getLevel())
                .orElseThrow().value().assemble(separatorInput, helper.getLevel().registryAccess());
        check(helper, separatorResult.is(ModItems.FRACTION_SPACER_ITEM.get()),
                "Iron Bars, Steel Shell, Iron Bars must make the source tower separator");
        FractionTowerSeparatorBlock separator = ModBlocks.FRACTION_SPACER.get();
        BlockPos separatorCore = helper.absolutePos(new BlockPos(5, 5, 5));
        helper.setBlock(new BlockPos(5, 5, 5), separator.defaultBlockState());
        separator.setPlacedBy(helper.getLevel(), separatorCore, separator.defaultBlockState(),
                helper.makeMockPlayer(GameType.SURVIVAL), separatorResult);
        long separatorCells = FractionTowerSeparatorBlock.partPositions(separatorCore).stream()
                .filter(pos -> helper.getLevel().getBlockState(pos).is(ModBlocks.FRACTION_SPACER.get())).count();
        check(helper, separatorCells == 9,
                "The inert source separator must occupy exactly one full 3x3 layer between tower segments");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fourNormalRecipesPreserveExactHundredMillibucketSplits(GameTestHelper helper) {
        check(helper, FractionRecipes.recipeCount() == 4,
                "The table must contain the four registered normal source fractions");
        assertRecipe(helper, new BlockPos(2, 1, 2), FluidIdentifierItem.Selection.HEAVYOIL,
                ModFluids.HEAVYOIL.get(), ModFluids.BITUMEN.get(), 30, ModFluids.SMEAR.get(), 70);
        assertRecipe(helper, new BlockPos(5, 1, 2), FluidIdentifierItem.Selection.SMEAR,
                ModFluids.SMEAR.get(), ModFluids.HEATINGOIL.get(), 60, ModFluids.LUBRICANT.get(), 40);
        assertRecipe(helper, new BlockPos(2, 1, 5), FluidIdentifierItem.Selection.NAPHTHA,
                ModFluids.NAPHTHA.get(), ModFluids.HEATINGOIL.get(), 40, ModFluids.DIESEL.get(), 60);
        assertRecipe(helper, new BlockPos(5, 1, 5), FluidIdentifierItem.Selection.LIGHTOIL,
                ModFluids.LIGHTOIL.get(), ModFluids.DIESEL.get(), 40, ModFluids.KEROSENE.get(), 60);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedFractionStallsAtomically(GameTestHelper helper) {
        FractionTowerBlockEntity tower = bareTower(helper, new BlockPos(3, 1, 3));
        tower.configureInput(FluidIdentifierItem.Selection.HEAVYOIL);
        tower.tank(0).fill(new FluidStack(ModFluids.HEAVYOIL.get(), 100), IFluidHandler.FluidAction.EXECUTE);
        tower.tank(2).fill(new FluidStack(ModFluids.SMEAR.get(),
                FractionTowerBlockEntity.TANK_CAPACITY - 69), IFluidHandler.FluidAction.EXECUTE);
        check(helper, !tower.processFractionation() && tower.tank(0).getFluidAmount() == 100
                        && tower.tank(1).isEmpty()
                        && tower.tank(2).getFluidAmount() == FractionTowerBlockEntity.TANK_CAPACITY - 69,
                "Insufficient space in either output must preserve the input and both fractions atomically");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stackedSegmentsMoveFeedUpAndFractionsDown(GameTestHelper helper) {
        FractionTowerBlockEntity lower = bareTower(helper, new BlockPos(3, 1, 3));
        FractionTowerBlockEntity upper = bareTower(helper, new BlockPos(3, 4, 3));
        lower.configureInput(FluidIdentifierItem.Selection.NAPHTHA);
        upper.configureInput(FluidIdentifierItem.Selection.HEAVYOIL);
        lower.tank(0).fill(new FluidStack(ModFluids.NAPHTHA.get(), 1_000), IFluidHandler.FluidAction.EXECUTE);
        upper.tank(0).fill(new FluidStack(ModFluids.HEAVYOIL.get(), 500), IFluidHandler.FluidAction.EXECUTE);
        lower.synchronizeWithUpper(upper);
        check(helper, upper.configuredFluid() == FluidIdentifierItem.Selection.NAPHTHA
                        && lower.tank(0).isEmpty() && upper.tank(0).getFluidAmount() == 1_000,
                "The lower segment must synchronize type upward, clear mismatched upper fluid and move feed up");
        check(helper, upper.processFractionation(), "The upper segment must process the synchronized feed");
        lower.synchronizeWithUpper(upper);
        check(helper, lower.tank(1).getFluidAmount() == 40 && lower.tank(1).getFluid().is(ModFluids.HEATINGOIL.get())
                        && lower.tank(2).getFluidAmount() == 60 && lower.tank(2).getFluid().is(ModFluids.DIESEL.get())
                        && upper.tank(1).isEmpty() && upper.tank(2).isEmpty(),
                "Upper output fractions must be pulled back down into the lower segment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void identifierTankStateContainersAndGrateLevelsStayExact(GameTestHelper helper) {
        FractionTowerBlockEntity tower = bareTower(helper, new BlockPos(3, 1, 3));
        tower.configureInput(FluidIdentifierItem.Selection.SMEAR);
        tower.tank(0).fill(new FluidStack(ModFluids.SMEAR.get(), 250), IFluidHandler.FluidAction.EXECUTE);
        check(helper, tower.processFractionation(), "Industrial Oil must be a valid normal fraction input");
        CompoundTag saved = tower.saveWithoutMetadata(helper.getLevel().registryAccess());
        FractionTowerBlockEntity loaded = bareTower(helper, new BlockPos(6, 1, 3));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.configuredFluid() == FluidIdentifierItem.Selection.SMEAR
                        && loaded.tank(0).getFluidAmount() == 150
                        && loaded.tank(1).getFluidAmount() == 60
                        && loaded.tank(2).getFluidAmount() == 40,
                "The identifier and all three 4,000 mB tanks must round-trip through NBT");
        loaded.configureInput(FluidIdentifierItem.Selection.GAS);
        check(helper, loaded.configuredFluid() == FluidIdentifierItem.Selection.NONE
                        && loaded.tank(0).isEmpty() && loaded.tank(1).isEmpty() && loaded.tank(2).isEmpty(),
                "An unsupported source identifier must reset the segment and all tanks to None");

        check(helper, UniversalFluidTankItem.ContainedFluid.fromFluid(ModFluids.BITUMEN.get())
                        == UniversalFluidTankItem.ContainedFluid.BITUMEN
                        && SourceFluidContainerItem.ContainedFluid.fromFluid(ModFluids.KEROSENE.get())
                        == SourceFluidContainerItem.ContainedFluid.KEROSENE
                        && SourceFluidContainerItem.ContainedFluid.BITUMEN.containerColor() == 0x5A5877
                        && SourceFluidContainerItem.ContainedFluid.KEROSENE.containerColor() == 0xFF377D,
                "Fraction fluids must retain source universal-tank and canister identities/colors");
        check(helper, SteelGrateBlock.placementLevel(Direction.UP, 0.9D) == 0
                        && SteelGrateBlock.placementLevel(Direction.DOWN, 0.1D) == 7
                        && SteelGrateBlock.placementLevel(Direction.NORTH, 0.49D) == 3,
                "Ordinary Steel Grate placement must preserve the source eighth-block level mapping");
        helper.succeed();
    }

    private static void assertRecipe(GameTestHelper helper, BlockPos position,
                                     FluidIdentifierItem.Selection selection, Fluid input,
                                     Fluid left, int leftAmount, Fluid right, int rightAmount) {
        FractionTowerBlockEntity tower = bareTower(helper, position);
        tower.configureInput(selection);
        check(helper, tower.fluidHandler().fill(new FluidStack(input, 100),
                        IFluidHandler.FluidAction.EXECUTE) == 100 && tower.processFractionation()
                        && tower.tank(0).isEmpty()
                        && tower.tank(1).getFluid().is(left) && tower.tank(1).getFluidAmount() == leftAmount
                        && tower.tank(2).getFluid().is(right) && tower.tank(2).getFluidAmount() == rightAmount,
                "The source 100 mB fraction split for " + selection.id() + " must remain exact");
    }

    private static FractionTowerBlockEntity placeTower(GameTestHelper helper, BlockPos relativeCore) {
        FractionTowerBlock block = ModBlocks.MACHINE_FRACTION_TOWER.get();
        var state = block.defaultBlockState();
        helper.setBlock(relativeCore, state);
        BlockPos absoluteCore = helper.absolutePos(relativeCore);
        block.setPlacedBy(helper.getLevel(), absoluteCore, state, helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.MACHINE_FRACTION_TOWER_ITEM.get()));
        return (FractionTowerBlockEntity) helper.getLevel().getBlockEntity(absoluteCore);
    }

    private static FractionTowerBlockEntity bareTower(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_FRACTION_TOWER.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
