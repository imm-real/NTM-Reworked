package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ArcFurnaceBlock;
import com.hbm.ntm.blockentity.ArcFurnaceBlockEntity;
import com.hbm.ntm.blockentity.ArcFurnaceProxyBlockEntity;
import com.hbm.ntm.item.ArcElectrodeItem;
import com.hbm.ntm.item.CastPlateItem;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.recipe.ArcFurnaceRecipes;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArcFurnaceGameTests {
    private ArcFurnaceGameTests() { }

    @GameTest(template = "empty")
    public static void solidSiliconTablePreservesEveryDependencyCompleteSourceRecipe(GameTestHelper helper) {
        checkRecipe(helper, new ItemStack(Items.SAND), 1);
        checkRecipe(helper, new ItemStack(Items.RED_SAND), 1);
        checkRecipe(helper, new ItemStack(Items.FLINT), 4);
        checkRecipe(helper, new ItemStack(Items.QUARTZ), 3);
        checkRecipe(helper, new ItemStack(ModItems.get("powder_quartz").get()), 3);
        checkRecipe(helper, new ItemStack(Items.QUARTZ_BLOCK), 12);
        checkRecipe(helper, new ItemStack(ModItems.get("ingot_asbestos").get()), 4);
        checkRecipe(helper, new ItemStack(ModItems.get("powder_asbestos").get()), 4);
        check(helper, ArcFurnaceRecipes.all().size() == 7
                        && ArcFurnaceRecipes.find(new ItemStack(Blocks.DIRT)) == null,
                "The registered Arc Furnace table must expose seven source entries and reject unrelated items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceFootprintUsesOneHundredFortyCellsAndSixLowerPorts(GameTestHelper helper) {
        BlockPos relativeCore = new BlockPos(5, 1, 5);
        ArcFurnaceBlockEntity furnace = placeFurnace(helper, relativeCore, Direction.NORTH);
        BlockPos core = furnace.getBlockPos();
        var positions = ArcFurnaceBlock.partPositions(core, Direction.NORTH);
        check(helper, positions.size() == 140 && new HashSet<>(positions).size() == 140,
                "The source 5x5x5 body and overlapping 3x2x5 rear check must form 140 unique cells");

        int cores = 0;
        int ports = 0;
        for (BlockPos part : positions) {
            var state = helper.getLevel().getBlockState(part);
            check(helper, state.is(ModBlocks.MACHINE_ARC_FURNACE.get())
                            && ArcFurnaceBlock.corePosition(part, state).equals(core),
                    "Every Arc Furnace cell must point back to the same source core");
            if (helper.getLevel().getBlockEntity(part) instanceof ArcFurnaceBlockEntity) cores++;
            if (helper.getLevel().getBlockEntity(part) instanceof ArcFurnaceProxyBlockEntity) {
                ports++;
                check(helper, ArcFurnaceBlock.isPort(state) && state.getValue(ArcFurnaceBlock.Y) == 0,
                        "Only the six source lower connection cells may host capability proxies");
            }
        }
        check(helper, cores == 1 && ports == 6,
                "The Electric Arc Furnace must contain one core and six lower connection proxies");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneSandBatchTakesFourHundredTicksWorthOfPowerAndOneElectrodeWear(GameTestHelper helper) {
        ArcFurnaceBlockEntity furnace = bareFurnace(helper, new BlockPos(3, 1, 3));
        loadFreshGraphite(furnace);
        furnace.setItem(ArcFurnaceBlockEntity.GRID_START, new ItemStack(Items.SAND));
        furnace.setLidForTest(0F);
        furnace.setProgressForTest(399F / 400F);
        furnace.setPower(1_000L);

        tick(helper, furnace);

        ItemStack result = furnace.getItem(ArcFurnaceBlockEntity.GRID_START);
        check(helper, result.is(ModItems.get("nugget_silicon").get()) && result.getCount() == 1,
                "Tick 400 must replace one Sand with one Silicon Nugget in its grid cell");
        check(helper, furnace.getPower() == 0L && furnace.progress() == 0F && furnace.delay() == 120,
                "A default completion must draw 1,000 HE on tick 400 and start the exact 120-tick cooldown");
        for (int slot = 0; slot < 3; slot++) {
            check(helper, ArcElectrodeItem.durability(furnace.getItem(slot)) == 1,
                    "Every completed batch must apply exactly one wear to each of the three electrodes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void graphiteElectrodesBecomeMoltenAfterTenCompletedBatches(GameTestHelper helper) {
        ArcFurnaceBlockEntity furnace = bareFurnace(helper, new BlockPos(3, 1, 3));
        loadFreshGraphite(furnace);
        furnace.setLidForTest(0F);
        for (int batch = 0; batch < 10; batch++) {
            furnace.setItem(ArcFurnaceBlockEntity.GRID_START, new ItemStack(Items.FLINT));
            furnace.setProgressForTest(399F / 400F);
            furnace.setDelayForTest(0);
            furnace.setPower(1_000L);
            tick(helper, furnace);
        }
        for (int slot = 0; slot < 3; slot++) {
            ItemStack electrode = furnace.getItem(slot);
            check(helper, electrode.is(ModItems.ARC_ELECTRODE_BURNT.get())
                            && ArcElectrodeItem.type(electrode) == ArcElectrodeItem.ElectrodeType.GRAPHITE,
                    "A Graphite Electrode must become its matching Molten Graphite Electrode on batch ten");
        }

        var smelting = helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "arc_electrode_burnt_graphite")).orElseThrow().value();
        check(helper, smelting.getIngredients().getFirst().test(furnace.getItem(0))
                        && smelting.getResultItem(helper.getLevel().registryAccess())
                        .is(ModItems.get("ingot_graphite").get()),
                "The source recovery route must smelt a Molten Graphite Electrode back into Graphite");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void assemblyRecipePreservesSourceConstructionCosts(GameTestHelper helper) {
        AssemblyRecipe recipe = AssemblyRecipes.byName("ass.arcfurnace");
        check(helper, recipe != null && recipe.duration() == 200 && recipe.power() == 100L
                        && recipe.output().is(ModItems.MACHINE_ARC_FURNACE_ITEM.get())
                        && recipe.inputs().size() == 6,
                "ass.arcfurnace must output the Electric Arc Furnace in 200 ticks at 100 HE/t");
        check(helper, recipe.inputs().get(0).count() == 12
                        && recipe.inputs().get(0).matches(new ItemStack(ModItems.CONCRETE_SMOOTH_ITEM.get(), 12))
                        && recipe.inputs().get(1).matches(new ItemStack(ModItems.PLATE_POLYMER.get(), 8))
                        && recipe.inputs().get(2).matches(new ItemStack(ModItems.get("ingot_firebrick").get(), 16))
                        && recipe.inputs().get(3).matches(CastPlateItem.create(ModItems.PLATE_CAST.get(),
                        CastPlateItem.CastPlateMaterial.STEEL, 8))
                        && recipe.inputs().get(4).matches(new ItemStack(ModItems.MACHINE_TRANSFORMER_ITEM.get()))
                        && recipe.inputs().get(5).matches(CircuitItem.create(ModItems.CIRCUIT.get(),
                        CircuitItem.CircuitType.ANALOG, 1)),
                "Arc Furnace construction must retain 12 concrete, 8 polymer, 16 firebrick, 8 Cast Steel, a Transformer and Analog Circuit");
        helper.succeed();
    }

    private static ArcFurnaceBlockEntity placeFurnace(GameTestHelper helper, BlockPos relativeCore,
                                                        Direction facing) {
        ArcFurnaceBlock block = ModBlocks.MACHINE_ARC_FURNACE.get();
        BlockPos absoluteCore = helper.absolutePos(relativeCore);
        BlockPos clicked = absoluteCore.relative(facing, 2);
        var clickedState = block.stateForPart(clicked, absoluteCore, facing);
        helper.getLevel().setBlock(clicked, clickedState, 3);
        block.setPlacedBy(helper.getLevel(), clicked, clickedState,
                helper.makeMockPlayer(GameType.SURVIVAL), new ItemStack(ModItems.MACHINE_ARC_FURNACE_ITEM.get()));
        return (ArcFurnaceBlockEntity) helper.getLevel().getBlockEntity(absoluteCore);
    }

    private static ArcFurnaceBlockEntity bareFurnace(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_ARC_FURNACE.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void loadFreshGraphite(ArcFurnaceBlockEntity furnace) {
        for (int slot = 0; slot < 3; slot++) {
            furnace.setItem(slot, ArcElectrodeItem.create(ModItems.ARC_ELECTRODE.get(),
                    ArcElectrodeItem.ElectrodeType.GRAPHITE, 1));
        }
    }

    private static void checkRecipe(GameTestHelper helper, ItemStack input, int count) {
        ArcFurnaceRecipes.Recipe recipe = ArcFurnaceRecipes.find(input);
        check(helper, recipe != null && recipe.output().is(ModItems.get("nugget_silicon").get())
                        && recipe.output().getCount() == count,
                input.getHoverName().getString() + " must arc-smelt into exactly " + count + " Silicon Nuggets");
    }

    private static void tick(GameTestHelper helper, ArcFurnaceBlockEntity furnace) {
        ArcFurnaceBlockEntity.tick(helper.getLevel(), furnace.getBlockPos(), furnace.getBlockState(), furnace);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
