package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.block.HeatingOvenBlock;
import com.hbm.ntm.block.ThermalMultiblockBlock;
import com.hbm.ntm.blockentity.FireboxBlockEntity;
import com.hbm.ntm.blockentity.ThermalProxyBlockEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeatingOvenGameTests {
    private HeatingOvenGameTests() { }

    @GameTest(template = "empty")
    public static void placementBuildsExactNineCellStructureAndDummyBreakTearsItDown(GameTestHelper helper) {
        BlockPos clicked = new BlockPos(4, 1, 3);
        Direction facing = Direction.NORTH;
        HeatingOvenBlock block = ModBlocks.HEATER_OVEN.get();
        BlockState clickedState = block.defaultBlockState()
                .setValue(ThermalMultiblockBlock.FACING, facing)
                .setValue(ThermalMultiblockBlock.CORE_X, 1)
                .setValue(ThermalMultiblockBlock.CORE_Y, 1)
                .setValue(ThermalMultiblockBlock.CORE_Z, 2);
        helper.setBlock(clicked, clickedState);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(clicked), clickedState,
                helper.makeMockPlayer(GameType.SURVIVAL), new ItemStack(ModItems.HEATER_OVEN_ITEM.get()));

        BlockPos core = clicked.relative(facing.getOpposite());
        int cells = 0;
        int cores = 0;
        int proxies = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos part = core.offset(x, 0, z);
                BlockState state = helper.getBlockState(part);
                check(helper, state.is(block), "Every source 3x1x3 Heating Oven cell must be present");
                check(helper, ThermalMultiblockBlock.corePosition(part, state).equals(core),
                        "Every Oven part must resolve to the same core");
                cells++;
                if (helper.getLevel().getBlockEntity(helper.absolutePos(part)) instanceof FireboxBlockEntity) cores++;
                if (helper.getLevel().getBlockEntity(helper.absolutePos(part)) instanceof ThermalProxyBlockEntity) proxies++;
            }
        }
        check(helper, cells == 9 && cores == 1 && proxies == 8,
                "The Oven must have nine cells, one core and eight inventory/fluid proxies");

        helper.destroyBlock(core.offset(1, 0, 1));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                check(helper, !helper.getBlockState(core.offset(x, 0, z)).is(block),
                        "Breaking any Oven part must dismantle all nine cells");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void coalUsesExactOneEighthDurationAndFivefoldBaseHeat(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 1, 3);
        FireboxBlockEntity oven = bareOven(helper, position);
        oven.setItem(0, new ItemStack(Items.COAL));

        tick(helper, oven);
        check(helper, oven.isHeatingOven() && oven.maxHeat() == 500_000,
                "The Heating Oven must use its distinct 500,000 TU buffer");
        check(helper, oven.maxBurnTime() == 250 && oven.burnTime() == 250,
                "Coal's modified 2,000 ticks must truncate to the source 250 Oven ticks");
        check(helper, oven.burnHeat() == 1_000 && oven.heatEnergy() == 1_000,
                "Coal must apply its 2x category modifier to the Oven's 500 TU base heat");

        tick(helper, oven);
        check(helper, oven.burnTime() == 249 && oven.heatEnergy() == 2_000,
                "The second Oven tick must consume one burn tick and add another 1,000 TU");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bottomSourceIsFullyDrainedForHalfRecoveryBeforeCooling(GameTestHelper helper) {
        BlockPos ovenPosition = new BlockPos(3, 2, 3);
        FireboxBlockEntity source = bareFirebox(helper, ovenPosition.below());
        FireboxBlockEntity oven = bareOven(helper, ovenPosition);
        setHeat(helper, source, 101);

        tick(helper, oven);
        check(helper, source.getHeatStored() == 0,
                "The Oven must consume the full available amount below despite its 50% efficiency");
        check(helper, oven.heatEnergy() == 49,
                "101 TU must truncate to 50 recovered TU before the idle base tick cools it to 49");

        setHeat(helper, source, 100);
        oven.setItem(0, new ItemStack(Items.COAL));
        tick(helper, oven);
        check(helper, source.getHeatStored() == 0 && oven.heatEnergy() == 1_099,
                "Bottom recovery must run before ignition, then the active tick must add 1,000 TU without cooling");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceStatePersistsWithSharedFireboxNbtKeys(GameTestHelper helper) {
        FireboxBlockEntity oven = bareOven(helper, new BlockPos(2, 1, 2));
        oven.setItem(1, new ItemStack(ModItems.SOLID_FUEL.get(), 2));
        CompoundTag state = oven.saveWithoutMetadata(helper.getLevel().registryAccess());
        state.putInt("maxBurnTime", 400);
        state.putInt("burnTime", 399);
        state.putInt("burnHeat", 1_500);
        state.putInt("heatEnergy", 123_456);

        FireboxBlockEntity loaded = bareOven(helper, new BlockPos(6, 1, 6));
        loaded.loadWithComponents(state, helper.getLevel().registryAccess());
        check(helper, loaded.getItem(1).is(ModItems.SOLID_FUEL.get()) && loaded.getItem(1).getCount() == 2,
                "Both source fuel slots must persist");
        check(helper, loaded.maxBurnTime() == 400 && loaded.burnTime() == 399
                        && loaded.burnHeat() == 1_500 && loaded.heatEnergy() == 123_456,
                "The Oven must retain the inherited source Firebox NBT keys");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tierTwoConstructionAndRecyclingMatchSource(GameTestHelper helper) {
        AnvilRecipes.Construction recipe = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "anvil/heater_oven"));
        check(helper, recipe != null && recipe.tierLower() == 2 && recipe.inputs().size() == 3,
                "The Heating Oven must remain a three-input Tier 2 construction operation");
        check(helper, recipe.inputs().get(0).count() == 16 && recipe.inputs().get(1).count() == 4
                        && recipe.inputs().get(2).count() == 8
                        && recipe.icon().is(ModItems.HEATER_OVEN_ITEM.get()),
                "Construction must use 16 Firebricks, four Steel Plates and eight Copper ingots");

        AnvilRecipes.Construction recycling = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "anvil/recycle_heater_oven"));
        ItemStack first = recycling.outputs().get(0).stack().get();
        ItemStack second = recycling.outputs().get(1).stack().get();
        check(helper, recycling.inputs().getFirst().matches(new ItemStack(ModItems.HEATER_OVEN_ITEM.get()))
                        && first.is(ModItems.get("ingot_firebrick").get()) && first.getCount() == 16
                        && second.is(ModItems.get("ingot_copper").get()) && second.getCount() == 8,
                "Recycling must return only the source Firebricks and Copper, not the Steel Plates");
        helper.succeed();
    }

    private static FireboxBlockEntity bareOven(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, coreState(ModBlocks.HEATER_OVEN.get().defaultBlockState()));
        return helper.getBlockEntity(position);
    }

    private static FireboxBlockEntity bareFirebox(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, coreState(ModBlocks.HEATER_FIREBOX.get().defaultBlockState()));
        return helper.getBlockEntity(position);
    }

    private static BlockState coreState(BlockState state) {
        return state.setValue(ThermalMultiblockBlock.FACING, Direction.SOUTH)
                .setValue(ThermalMultiblockBlock.CORE_X, 1)
                .setValue(ThermalMultiblockBlock.CORE_Y, 1)
                .setValue(ThermalMultiblockBlock.CORE_Z, 1);
    }

    private static void setHeat(GameTestHelper helper, FireboxBlockEntity source, int heat) {
        CompoundTag state = source.saveWithoutMetadata(helper.getLevel().registryAccess());
        state.putInt("heatEnergy", heat);
        source.loadWithComponents(state, helper.getLevel().registryAccess());
    }

    private static void tick(GameTestHelper helper, FireboxBlockEntity oven) {
        FireboxBlockEntity.tick(helper.getLevel(), oven.getBlockPos(), oven.getBlockState(), oven);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
