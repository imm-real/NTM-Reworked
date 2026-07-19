package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.ThermalMultiblockBlock;
import com.hbm.ntm.blockentity.AshpitBlockEntity;
import com.hbm.ntm.blockentity.FireboxBlockEntity;
import com.hbm.ntm.blockentity.MachineShredderBlockEntity;
import com.hbm.ntm.blockentity.StirlingBlockEntity;
import com.hbm.ntm.blockentity.ThermalProxyBlockEntity;
import com.hbm.ntm.energy.HeConnector;
import com.hbm.ntm.entity.CogEntity;
import com.hbm.ntm.item.AshItem;
import com.hbm.ntm.pollution.PollutionData;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModEntities;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThermalGeneratorGameTests {
    private ThermalGeneratorGameTests() {
    }

    @GameTest(template = "empty")
    public static void stirlingPlacementBuildsAndDummyBreakDismantlesFullStructure(GameTestHelper helper) {
        BlockPos clicked = new BlockPos(3, 1, 2);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockState clickedState = ModBlocks.MACHINE_STIRLING.get().defaultBlockState()
                .setValue(ThermalMultiblockBlock.FACING, Direction.NORTH)
                .setValue(ThermalMultiblockBlock.CORE_X, 1)
                .setValue(ThermalMultiblockBlock.CORE_Y, 1)
                .setValue(ThermalMultiblockBlock.CORE_Z, 2);
        helper.setBlock(clicked, clickedState);
        ModBlocks.MACHINE_STIRLING.get().setPlacedBy(helper.getLevel(), helper.absolutePos(clicked),
                clickedState, player, new ItemStack(ModItems.MACHINE_STIRLING_ITEM.get()));

        BlockPos core = null;
        int partCount = 0;
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 1; z <= 5; z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (helper.getBlockState(position).is(ModBlocks.MACHINE_STIRLING.get())) {
                        partCount++;
                        if (helper.getLevel().getBlockEntity(helper.absolutePos(position)) instanceof StirlingBlockEntity) {
                            core = position;
                        }
                    }
                }
            }
        }
        check(helper, core != null, "Placing the Stirling item should create one core block entity");
        check(helper, partCount == 18, "The Stirling Engine should occupy a complete 3x2x3 volume");
        checkPowerPort(helper, core.east(), Direction.EAST);
        checkPowerPort(helper, core.west(), Direction.WEST);
        checkPowerPort(helper, core.south(), Direction.SOUTH);
        checkPowerPort(helper, core.north(), Direction.NORTH);

        BlockPos dummy = core.offset(1, 1, 1);
        helper.destroyBlock(dummy);
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    check(helper, !helper.getBlockState(core.offset(x, y, z)).is(ModBlocks.MACHINE_STIRLING.get()),
                            "Breaking any Stirling part should dismantle every dummy and the core");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireboxIgnitionPreservesFirstBurnTick(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 1, 2);
        FireboxBlockEntity firebox = placeFirebox(helper, position);
        firebox.setItem(0, new ItemStack(Items.COAL));

        tickFirebox(helper, position, firebox);
        check(helper, firebox.maxBurnTime() == 2_000 && firebox.burnTime() == 2_000,
                "Coal should ignite for 2,000 ticks without decrementing on the ignition tick");
        check(helper, firebox.heatEnergy() == 200 && firebox.burnHeat() == 200,
                "Coal should immediately add 200 TU on ignition");

        tickFirebox(helper, position, firebox);
        check(helper, firebox.burnTime() == 1_999 && firebox.heatEnergy() == 400,
                "The second tick should consume the first burn tick and add another 200 TU");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireboxPausesBurningAtFullHeat(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 1, 2);
        FireboxBlockEntity firebox = placeFirebox(helper, position);
        setFireboxHeat(helper, firebox, FireboxBlockEntity.MAX_HEAT - 100);
        firebox.setItem(0, new ItemStack(Items.COAL));

        tickFirebox(helper, position, firebox);
        check(helper, firebox.heatEnergy() == FireboxBlockEntity.MAX_HEAT && firebox.burnTime() == 2_000,
                "Ignition should fill the heat buffer without consuming the first burn tick");
        tickFirebox(helper, position, firebox);
        check(helper, firebox.heatEnergy() == FireboxBlockEntity.MAX_HEAT && firebox.burnTime() == 2_000,
                "A full Firebox must pause its active fuel instead of wasting burn time");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireboxAppliesOriginalRocketAndBalefireModifiers(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 1, 2);
        FireboxBlockEntity firebox = placeFirebox(helper, position);
        firebox.setItem(0, new ItemStack(ModItems.ROCKET_FUEL.get()));

        tickFirebox(helper, position, firebox);
        check(helper, firebox.maxBurnTime() == 9_600 && firebox.burnTime() == 9_600
                        && firebox.burnHeat() == 500,
                "Rocket propellant must receive x1.5 duration and x5 Firebox heat");

        firebox.clearContent();
        CompoundTag reset = firebox.saveWithoutMetadata(helper.getLevel().registryAccess());
        reset.putInt("burnTime", 0);
        reset.putInt("maxBurnTime", 0);
        reset.putInt("heatEnergy", 0);
        firebox.loadWithComponents(reset, helper.getLevel().registryAccess());
        firebox.setItem(0, new ItemStack(ModItems.SOLID_FUEL_BF.get()));
        tickFirebox(helper, position, firebox);
        check(helper, firebox.maxBurnTime() == 16_000 && firebox.burnTime() == 16_000
                        && firebox.burnHeat() == 1_500,
                "Balefire solid fuel must receive x0.5 duration and x15 Firebox heat");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ashpitReceivesFuelSpecificAshAndPreservesCounterBug(GameTestHelper helper) {
        BlockPos ashpitPosition = new BlockPos(2, 1, 2);
        BlockPos fireboxPosition = ashpitPosition.above();
        AshpitBlockEntity ashpit = placeAshpit(helper, ashpitPosition);
        FireboxBlockEntity firebox = placeFirebox(helper, fireboxPosition);
        firebox.setItem(0, new ItemStack(Items.COAL));

        tickFirebox(helper, fireboxPosition, firebox);
        AshpitBlockEntity.tick(helper.getLevel(), helper.absolutePos(ashpitPosition),
                helper.getBlockState(ashpitPosition), ashpit);
        check(helper, ashpit.getItem(0).is(ModItems.POWDER_ASH.get()),
                "A coal ignition should create an ash item once the Ashpit ticks");
        check(helper, AshItem.type(ashpit.getItem(0)) == AshItem.AshType.COAL,
                "Coal must create Coal Ash rather than generic or wood ash");
        CompoundTag saved = ashpit.saveWithoutMetadata(helper.getLevel().registryAccess());
        check(helper, saved.getInt("ashLevelWood") == -AshpitBlockEntity.THRESHOLD_COAL,
                "Creating ash in an empty slot must preserve the original wrong wood-counter subtraction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stirlingPullsTenPercentAndOutputsHalfAsHe(GameTestHelper helper) {
        BlockPos fireboxPosition = new BlockPos(3, 1, 3);
        BlockPos stirlingPosition = fireboxPosition.above();
        FireboxBlockEntity firebox = placeFirebox(helper, fireboxPosition);
        StirlingBlockEntity stirling = placeStirling(helper, stirlingPosition);
        MachineShredderBlockEntity receiver = placeReceiver(helper, stirlingPosition.east(2));
        setFireboxHeat(helper, firebox, 3_000);

        StirlingBlockEntity.tick(helper.getLevel(), helper.absolutePos(stirlingPosition),
                helper.getBlockState(stirlingPosition), stirling);
        check(helper, firebox.getHeatStored() == 2_700,
                "The Stirling Engine should pull floor(source TU * 0.1)");
        check(helper, receiver.getPower() == 150,
                "A safe 300 TU/t intake should produce and directly provide 150 HE");
        check(helper, stirling.getPower() == 0,
                "The transient Stirling buffer should be empty after direct provision");
        CompoundTag update = stirling.getUpdateTag(helper.getLevel().registryAccess());
        check(helper, update.getLong("powerBuffer") == 150 && update.getInt("heat") == 300,
                "The client snapshot must retain pre-provision HE and TU for animation and the look overlay");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void overspeedEjectsRecoverableCogAfterThreeHundredOneTicks(GameTestHelper helper) {
        BlockPos fireboxPosition = new BlockPos(3, 1, 3);
        BlockPos stirlingPosition = fireboxPosition.above();
        FireboxBlockEntity firebox = placeFirebox(helper, fireboxPosition);
        StirlingBlockEntity stirling = placeStirling(helper, stirlingPosition);

        for (int tick = 0; tick <= StirlingBlockEntity.OVERSPEED_LIMIT; tick++) {
            setFireboxHeat(helper, firebox, 4_000);
            StirlingBlockEntity.tick(helper.getLevel(), helper.absolutePos(stirlingPosition),
                    helper.getBlockState(stirlingPosition), stirling);
        }

        check(helper, !stirling.hasCog(), "The normal Stirling gear should fail on the 301st over-limit tick");
        AABB bounds = new AABB(helper.absolutePos(stirlingPosition)).inflate(8.0D);
        check(helper, !helper.getLevel().getEntitiesOfClass(CogEntity.class, bounds).isEmpty(),
                "Overspeed failure must launch a physical recoverable cog entity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ejectedCogStopsAsRecoverableRestingEntity(GameTestHelper helper) {
        BlockPos floor = new BlockPos(3, 1, 3);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos absoluteFloor = helper.absolutePos(floor);
        CogEntity cog = new CogEntity(ModEntities.COG.get(), helper.getLevel());
        cog.setPos(absoluteFloor.getX() + 0.5D, absoluteFloor.getY() + 4.0D,
                absoluteFloor.getZ() + 0.5D);
        cog.setDeltaMovement(0.0D, -0.2D, 0.0D);
        helper.getLevel().addFreshEntity(cog);

        Vec3[] restingPosition = new Vec3[1];
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    check(helper, cog.embedded(),
                            "A slow cog impact must enter the original stopped orientation state");
                    restingPosition[0] = cog.position();
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    check(helper, cog.isAlive() && cog.embedded(),
                            "A stopped cog must remain a recoverable entity without resuming flight");
                    check(helper, cog.position().distanceToSqr(restingPosition[0]) < 0.000001D,
                            "A stopped cog must remain fixed at its final impact position");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void fireboxExposesOutputOnlySmokeTankOnOriginalSides(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 1, 2);
        FireboxBlockEntity firebox = placeFirebox(helper, position);
        CompoundTag saved = firebox.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.putInt("smoke0", FireboxBlockEntity.SMOKE_CAPACITY);
        firebox.loadWithComponents(saved, helper.getLevel().registryAccess());

        IFluidHandler handler = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK, helper.absolutePos(position), Direction.UP);
        check(helper, handler != null, "The Firebox must expose its smoke tank horizontally and upward");
        check(helper, helper.getLevel().getCapability(
                        Capabilities.FluidHandler.BLOCK, helper.absolutePos(position), Direction.DOWN) == null,
                "The original Firebox smoke connection must reject the bottom face");
        check(helper, handler.drain(20, IFluidHandler.FluidAction.SIMULATE).getAmount() == 20
                        && firebox.smokeStored() == FireboxBlockEntity.SMOKE_CAPACITY,
                "Simulated smoke extraction must not modify the Firebox buffer");
        check(helper, handler.drain(20, IFluidHandler.FluidAction.EXECUTE).getAmount() == 20
                        && firebox.smokeStored() == FireboxBlockEntity.SMOKE_CAPACITY - 20,
                "Executed smoke extraction must drain the exact accepted amount");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void unventedFireboxOverflowsIntoPersistentSoot(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 1, 2);
        FireboxBlockEntity firebox = placeFirebox(helper, position);
        firebox.setItem(0, new ItemStack(Items.COAL));
        helper.startSequence()
                .thenExecuteAfter(110, () -> {
                    float soot = PollutionData.get(helper.getLevel()).get(
                            helper.absolutePos(position), PollutionData.Type.SOOT);
                    check(helper, soot > 0.0F,
                            "An unvented active Firebox should overflow its smoke buffer into soot pollution");
                })
                .thenSucceed();
    }

    private static FireboxBlockEntity placeFirebox(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, coreState(ModBlocks.HEATER_FIREBOX.get().defaultBlockState()));
        return helper.getBlockEntity(position);
    }

    private static AshpitBlockEntity placeAshpit(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, coreState(ModBlocks.MACHINE_ASHPIT.get().defaultBlockState()));
        return helper.getBlockEntity(position);
    }

    private static StirlingBlockEntity placeStirling(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, coreState(ModBlocks.MACHINE_STIRLING.get().defaultBlockState()));
        return helper.getBlockEntity(position);
    }

    private static MachineShredderBlockEntity placeReceiver(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_SHREDDER.get());
        return helper.getBlockEntity(position);
    }

    private static BlockState coreState(BlockState state) {
        return state.setValue(ThermalMultiblockBlock.FACING, Direction.SOUTH)
                .setValue(ThermalMultiblockBlock.CORE_X, 1)
                .setValue(ThermalMultiblockBlock.CORE_Y, 1)
                .setValue(ThermalMultiblockBlock.CORE_Z, 1);
    }

    private static void tickFirebox(GameTestHelper helper, BlockPos position, FireboxBlockEntity firebox) {
        FireboxBlockEntity.tick(helper.getLevel(), helper.absolutePos(position), helper.getBlockState(position), firebox);
    }

    private static void setFireboxHeat(GameTestHelper helper, FireboxBlockEntity firebox, int heat) {
        CompoundTag tag = firebox.saveWithoutMetadata(helper.getLevel().registryAccess());
        tag.putInt("heatEnergy", heat);
        firebox.loadWithComponents(tag, helper.getLevel().registryAccess());
    }

    private static void checkPowerPort(GameTestHelper helper, BlockPos position, Direction outward) {
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        check(helper, blockEntity instanceof ThermalProxyBlockEntity,
                "Each lower cardinal Stirling edge must expose its original power proxy");
        check(helper, blockEntity instanceof HeConnector connector && connector.canConnect(outward)
                        && !connector.canConnect(outward.getOpposite()),
                "A Stirling power proxy must connect only through its outward face");
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
