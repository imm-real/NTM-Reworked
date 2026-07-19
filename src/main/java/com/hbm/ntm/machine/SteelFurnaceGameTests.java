package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.block.SteelFurnaceBlock;
import com.hbm.ntm.blockentity.ElectricHeaterBlockEntity;
import com.hbm.ntm.blockentity.SteelFurnaceBlockEntity;
import com.hbm.ntm.blockentity.SteelFurnaceProxyBlockEntity;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SteelFurnaceGameTests {
    private SteelFurnaceGameTests() { }

    @GameTest(template = "empty")
    public static void placementBuildsExactEighteenCellsAndSeventeenInventoryProxies(GameTestHelper helper) {
        SteelFurnaceBlock block = ModBlocks.FURNACE_STEEL.get();
        BlockPos clicked = new BlockPos(4, 1, 4);
        Direction facing = Direction.SOUTH;
        BlockPos core = clicked.relative(facing.getOpposite());
        BlockState placed = block.stateForPart(clicked, core, facing);
        helper.setBlock(clicked, placed);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(clicked), placed,
                helper.makeMockPlayer(GameType.SURVIVAL), new ItemStack(ModItems.FURNACE_STEEL_ITEM.get()));

        int cells = 0;
        int cores = 0;
        int proxies = 0;
        for (BlockPos part : SteelFurnaceBlock.partPositions(core)) {
            BlockState state = helper.getBlockState(part);
            check(helper, state.is(block) && SteelFurnaceBlock.corePosition(part, state).equals(core),
                    "Every source 3x2x3 Steel Furnace cell must map to one core");
            BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(part));
            if (entity instanceof SteelFurnaceBlockEntity) cores++;
            else if (entity instanceof SteelFurnaceProxyBlockEntity proxy) {
                proxies++;
                check(helper, proxy.target() != null && proxy.getSlotsForFace(Direction.UP).length == 6,
                        "Every dummy cell must retain the source all-face inventory ProxyCombo");
                check(helper, helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                                helper.absolutePos(part), Direction.NORTH) != null,
                        "Every dummy cell must expose the delegated item capability");
            }
            cells++;
        }
        check(helper, cells == 18 && cores == 1 && proxies == 17,
                "The structure must contain one core and 17 inventory proxies");

        helper.destroyBlock(core.offset(1, 1, 0));
        for (BlockPos part : SteelFurnaceBlock.partPositions(core)) {
            check(helper, !helper.getBlockState(part).is(block),
                    "Breaking any Steel Furnace cell must dismantle all 18 cells");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heatPullAndCoolingPreserveSourceDiffusionQuirks(GameTestHelper helper) {
        BlockPos furnacePos = new BlockPos(4, 2, 4);
        SteelFurnaceBlockEntity furnace = bareFurnace(helper, furnacePos);
        helper.setBlock(furnacePos.below(), ModBlocks.HEATER_ELECTRIC.get().defaultBlockState());
        ElectricHeaterBlockEntity source = helper.getBlockEntity(furnacePos.below());

        setSourceHeat(helper, source, 100_000);
        furnace.setHeatForTest(0);
        furnace.pullHeatForTest();
        check(helper, furnace.heat() == 5_000 && source.getHeatStored() == 95_000,
                "A 100,000 TU delta must transfer exactly ceil(delta*0.05)=5,000 TU");

        setSourceHeat(helper, source, 5_000);
        furnace.setHeatForTest(5_000);
        furnace.pullHeatForTest();
        check(helper, furnace.heat() == 5_000 && source.getHeatStored() == 5_000,
                "Equal source and furnace heat must return without passive cooling");

        furnace.setHeatForTest(10_000);
        furnace.pullHeatForTest();
        check(helper, furnace.heat() == 9_990 && source.getHeatStored() == 5_000,
                "A colder source must not receive heat; the furnace must cool by max(heat/1000,1)");

        furnace.setHeatForTest(SteelFurnaceBlockEntity.MAX_HEAT);
        setSourceHeat(helper, source, 200_000);
        furnace.pullHeatForTest();
        check(helper, furnace.heat() == SteelFurnaceBlockEntity.MAX_HEAT && source.getHeatStored() == 200_000,
                "A full Steel Furnace must neither pull heat nor cool");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void threeLanesUseOnePrecomputedBurnInSourceOrder(GameTestHelper helper) {
        SteelFurnaceBlockEntity furnace = bareFurnace(helper, new BlockPos(4, 1, 4));
        for (int lane = 0; lane < 3; lane++) furnace.setItem(lane, new ItemStack(Items.RAW_IRON));
        furnace.setHeatForTest(100_000);
        tick(helper, furnace);
        check(helper, furnace.progress(0) == 6_666 && furnace.progress(1) == 6_666
                        && furnace.progress(2) == 6_666 && furnace.heat() == 80_002 && furnace.wasOn(),
                "All lanes must reuse burn=(100000-33333)/10=6666 while subtracting it in lane order");

        SteelFurnaceBlockEntity threshold = bareFurnace(helper, new BlockPos(8, 1, 4));
        // tryPullHeat() runs first every tick and passively cools a bare furnace by max(heat/1000,1)
        // (TileEntityFurnaceSteel lines 56/212), which would drop 33,333 below the threshold. A heat
        // Exactly MINIMUM_HEAT yields diff==0 and leaves the reservoir untouched.
        BlockPos thresholdSource = new BlockPos(8, 0, 4);
        helper.setBlock(thresholdSource, ModBlocks.HEATER_ELECTRIC.get().defaultBlockState());
        ElectricHeaterBlockEntity thresholdHeat = helper.getBlockEntity(thresholdSource);
        setSourceHeat(helper, thresholdHeat, SteelFurnaceBlockEntity.MINIMUM_HEAT);
        threshold.setItem(0, new ItemStack(Items.RAW_IRON));
        threshold.setHeatForTest(SteelFurnaceBlockEntity.MINIMUM_HEAT);
        tick(helper, threshold);
        check(helper, threshold.wasOn() && threshold.progress(0) == 0
                        && threshold.heat() == SteelFurnaceBlockEntity.MINIMUM_HEAT,
                "Exactly 33,333 TU must count as active while integer burn remains zero");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oreBonusProducesOneExtraResultEveryFourSmelts(GameTestHelper helper) {
        SteelFurnaceBlockEntity furnace = bareFurnace(helper, new BlockPos(4, 1, 4));
        furnace.setItem(0, new ItemStack(Items.IRON_ORE, 4));
        // Any lane whose lastItems entry is still null gets zeroed (TileEntityFurnaceSteel
        // lines 39/64-67), so one idle tick must first record the input; otherwise the very first
        // fast-forwarded smelt below would be discarded and only three of four would complete.
        furnace.setHeatForTest(0);
        tick(helper, furnace);
        for (int smelt = 0; smelt < 4; smelt++) {
            furnace.setHeatForTest(100_000);
            furnace.setProgressForTest(0, SteelFurnaceBlockEntity.PROCESS_TIME - 1);
            tick(helper, furnace);
        }
        check(helper, furnace.getItem(0).isEmpty() && furnace.getItem(3).is(Items.IRON_INGOT)
                        && furnace.getItem(3).getCount() == 5 && furnace.bonus(0) == 0,
                "Four ore smelts must produce four base ingots plus one 100% bonus ingot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inputIdentityAndPersistentLaneStateMatchSource(GameTestHelper helper) {
        SteelFurnaceBlockEntity furnace = bareFurnace(helper, new BlockPos(4, 1, 4));
        furnace.setItem(0, new ItemStack(Items.IRON_ORE, 2));
        furnace.setHeatForTest(100_000);
        tick(helper, furnace);
        furnace.setProgressForTest(0, 12_345);
        furnace.setBonusForTest(0, 75);

        CompoundTag saved = furnace.saveWithoutMetadata(helper.getLevel().registryAccess());
        SteelFurnaceBlockEntity loaded = bareFurnace(helper, new BlockPos(8, 1, 4));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.progress(0) == 12_345 && loaded.bonus(0) == 75
                        && loaded.heat() == 93_334 && loaded.getItem(0).getCount() == 2,
                "Heat, progress, bonus, inventory and previous-input identity must persist");

        loaded.setItem(0, new ItemStack(Items.COPPER_ORE));
        loaded.setHeatForTest(100_000);
        tick(helper, loaded);
        check(helper, loaded.progress(0) == 6_666 && loaded.bonus(0) == 0,
                "Changing item identity must reset progress and bonus before the new item advances");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tierTwoConstructionUsesExactFiveSourceInputs(GameTestHelper helper) {
        AnvilRecipes.Construction recipe = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "anvil/furnace_steel"));
        check(helper, recipe != null && recipe.tierLower() == 2 && !recipe.validForTier(1)
                        && recipe.validForTier(2) && recipe.inputs().size() == 5
                        && recipe.icon().is(ModItems.FURNACE_STEEL_ITEM.get()),
                "Steel Furnace construction must remain a five-input Tier 2 operation");
        check(helper, recipe.inputs().get(0).count() == 16
                        && recipe.inputs().get(0).matches(new ItemStack(Items.STONE_BRICKS))
                        && recipe.inputs().get(1).count() == 4
                        && recipe.inputs().get(1).matches(new ItemStack(Items.IRON_INGOT))
                        && recipe.inputs().get(2).count() == 16
                        && recipe.inputs().get(3).count() == 8
                        && recipe.inputs().get(4).count() == 16
                        && recipe.inputs().get(4).matches(new ItemStack(ModItems.STEEL_GRATE_ITEM.get())),
                "Construction must use 16 Stone Bricks, four Iron, 16 Steel Plates, eight Copper and 16 Steel Grates");
        helper.succeed();
    }

    private static SteelFurnaceBlockEntity bareFurnace(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.FURNACE_STEEL.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void tick(GameTestHelper helper, SteelFurnaceBlockEntity furnace) {
        SteelFurnaceBlockEntity.tick(helper.getLevel(), furnace.getBlockPos(), furnace.getBlockState(), furnace);
    }

    private static void setSourceHeat(GameTestHelper helper, ElectricHeaterBlockEntity source, int heat) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("heatEnergy", heat);
        source.loadWithComponents(tag, helper.getLevel().registryAccess());
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
