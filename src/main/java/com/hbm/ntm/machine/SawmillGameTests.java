package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ThermalMultiblockBlock;
import com.hbm.ntm.blockentity.FireboxBlockEntity;
import com.hbm.ntm.blockentity.SawmillBlockEntity;
import com.hbm.ntm.blockentity.ThermalProxyBlockEntity;
import com.hbm.ntm.entity.SawbladeEntity;
import com.hbm.ntm.item.SawmillMachineBlockItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SawmillGameTests {
    private SawmillGameTests() {
    }

    @GameTest(template = "empty")
    public static void placementBuildsEighteenCellsButOnlyFourInventoryProxies(GameTestHelper helper) {
        BlockPos clicked = new BlockPos(3, 1, 2);
        BlockState clickedState = ModBlocks.MACHINE_SAWMILL.get().defaultBlockState()
                .setValue(ThermalMultiblockBlock.FACING, Direction.NORTH)
                .setValue(ThermalMultiblockBlock.CORE_X, 1)
                .setValue(ThermalMultiblockBlock.CORE_Y, 1)
                .setValue(ThermalMultiblockBlock.CORE_Z, 2);
        helper.setBlock(clicked, clickedState);
        ModBlocks.MACHINE_SAWMILL.get().setPlacedBy(helper.getLevel(), helper.absolutePos(clicked), clickedState,
                helper.makeMockPlayer(GameType.SURVIVAL), new ItemStack(ModItems.MACHINE_SAWMILL_ITEM.get()));

        BlockPos core = clicked.relative(Direction.SOUTH);
        int parts = 0;
        int proxies = 0;
        for (int x = -1; x <= 1; x++) for (int y = 0; y < 2; y++) for (int z = -1; z <= 1; z++) {
            BlockPos part = core.offset(x, y, z);
            if (helper.getBlockState(part).is(ModBlocks.MACHINE_SAWMILL.get())) parts++;
            if (helper.getLevel().getBlockEntity(helper.absolutePos(part)) instanceof ThermalProxyBlockEntity) proxies++;
        }
        check(helper, parts == 18, "The Sawmill must occupy the source 3x2x3 volume");
        check(helper, proxies == 4, "Only the four lower cardinal cells may expose the inventory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceRecipesPreserveExactWoodOutputs(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 2, 3);
        SawmillBlockEntity sawmill = placeSawmill(helper, position);
        check(helper, sawmill.outputFor(new ItemStack(Items.OAK_LOG)).is(Items.OAK_PLANKS)
                        && sawmill.outputFor(new ItemStack(Items.OAK_LOG)).getCount() == 6,
                "A one-log crafting recipe must be scaled from four to six planks");
        check(helper, sawmill.outputFor(new ItemStack(Items.OAK_PLANKS)).is(Items.STICK)
                        && sawmill.outputFor(new ItemStack(Items.OAK_PLANKS)).getCount() == 6,
                "A plank must produce six sticks");
        check(helper, sawmill.outputFor(new ItemStack(Items.STICK)).is(ModItems.POWDER_SAWDUST.get()),
                "A wooden rod must produce one Sawdust");
        check(helper, sawmill.outputFor(new ItemStack(Items.OAK_SAPLING)).is(Items.STICK),
                "A sapling must produce one stick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void processingUsesTransientTenPercentHeatAndSixHundredWork(GameTestHelper helper) {
        BlockPos fireboxPosition = new BlockPos(3, 1, 3);
        BlockPos sawmillPosition = fireboxPosition.above();
        FireboxBlockEntity firebox = placeFirebox(helper, fireboxPosition);
        SawmillBlockEntity sawmill = placeSawmill(helper, sawmillPosition);
        sawmill.setItem(SawmillBlockEntity.INPUT_SLOT, new ItemStack(Items.OAK_PLANKS));

        for (int tick = 0; tick < 20; tick++) {
            setFireboxHeat(helper, firebox, 3_000);
            SawmillBlockEntity.tick(helper.getLevel(), helper.absolutePos(sawmillPosition),
                    helper.getBlockState(sawmillPosition), sawmill);
        }
        check(helper, sawmill.getItem(SawmillBlockEntity.INPUT_SLOT).isEmpty(),
                "Twenty 300-TU ticks must consume the single input");
        check(helper, sawmill.getItem(SawmillBlockEntity.OUTPUT_SLOT).is(Items.STICK)
                        && sawmill.getItem(SawmillBlockEntity.OUTPUT_SLOT).getCount() == 6,
                "Six hundred accumulated work must finish exactly one plank recipe");
        check(helper, firebox.getHeatStored() == 2_700,
                "The final tick must drain floor(source heat * 0.1)");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sustainedOverspeedThrowsRecoverableSawblade(GameTestHelper helper) {
        BlockPos fireboxPosition = new BlockPos(3, 1, 3);
        BlockPos sawmillPosition = fireboxPosition.above();
        FireboxBlockEntity firebox = placeFirebox(helper, fireboxPosition);
        SawmillBlockEntity sawmill = placeSawmill(helper, sawmillPosition);
        for (int tick = 0; tick <= SawmillBlockEntity.OVERSPEED_LIMIT; tick++) {
            setFireboxHeat(helper, firebox, 4_000);
            SawmillBlockEntity.tick(helper.getLevel(), helper.absolutePos(sawmillPosition),
                    helper.getBlockState(sawmillPosition), sawmill);
        }
        check(helper, !sawmill.hasBlade(), "The blade must fail on the 301st over-limit tick");
        AABB bounds = new AABB(helper.absolutePos(sawmillPosition)).inflate(8.0D);
        check(helper, !helper.getLevel().getEntitiesOfClass(SawbladeEntity.class, bounds).isEmpty(),
                "Overspeed must launch the physical recoverable blade entity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingBladeItemAndAutomationRulesRemainDistinct(GameTestHelper helper) {
        ItemStack missing = SawmillMachineBlockItem.withoutBlade(ModItems.MACHINE_SAWMILL_ITEM.get());
        check(helper, SawmillMachineBlockItem.isMissingBlade(missing),
                "The no-blade block item must retain its component marker");
        BlockPos position = new BlockPos(3, 2, 3);
        SawmillBlockEntity sawmill = placeSawmill(helper, position);
        check(helper, !sawmill.canPlaceItem(0, new ItemStack(Items.OAK_LOG, 2)),
                "Automation must reject input stacks larger than one");
        check(helper, sawmill.canPlaceItem(0, new ItemStack(Items.OAK_LOG)),
                "Automation must accept one valid wood input while every slot is empty");
        check(helper, sawmill.canTakeItemThroughFace(1, new ItemStack(Items.STICK), Direction.DOWN)
                        && !sawmill.canTakeItemThroughFace(0, new ItemStack(Items.OAK_LOG), Direction.DOWN),
                "Only the two output slots may be extracted");
        helper.succeed();
    }

    private static SawmillBlockEntity placeSawmill(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, coreState(ModBlocks.MACHINE_SAWMILL.get().defaultBlockState()));
        return helper.getBlockEntity(position);
    }

    private static FireboxBlockEntity placeFirebox(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, coreState(ModBlocks.HEATER_FIREBOX.get().defaultBlockState()));
        return helper.getBlockEntity(position);
    }

    private static BlockState coreState(BlockState state) {
        return state.setValue(ThermalMultiblockBlock.FACING, Direction.SOUTH)
                .setValue(ThermalMultiblockBlock.CORE_X, 1)
                .setValue(ThermalMultiblockBlock.CORE_Y, 1)
                .setValue(ThermalMultiblockBlock.CORE_Z, 1);
    }

    private static void setFireboxHeat(GameTestHelper helper, FireboxBlockEntity firebox, int heat) {
        CompoundTag tag = firebox.saveWithoutMetadata(helper.getLevel().registryAccess());
        tag.putInt("heatEnergy", heat);
        firebox.loadWithComponents(tag, helper.getLevel().registryAccess());
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
