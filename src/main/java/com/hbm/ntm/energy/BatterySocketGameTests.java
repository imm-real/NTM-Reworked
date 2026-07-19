package com.hbm.ntm.energy;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.BatterySocketBlock;
import com.hbm.ntm.blockentity.BatterySocketBlockEntity;
import com.hbm.ntm.blockentity.BatterySocketProxyBlockEntity;
import com.hbm.ntm.blockentity.HeCableBlockEntity;
import com.hbm.ntm.blockentity.MachineShredderBlockEntity;
import com.hbm.ntm.item.BatteryPackItem;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BatterySocketGameTests {
    private BatterySocketGameTests() {
    }

    @GameTest(template = "empty")
    public static void batteryPackConstantsMatchSource(GameTestHelper helper) {
        checkType(helper, BatteryPackItem.BatteryType.BATTERY_REDSTONE, 1_800_000L, 1_000L, 100L);
        checkType(helper, BatteryPackItem.BatteryType.BATTERY_LEAD, 18_000_000L, 10_000L, 1_000L);
        checkType(helper, BatteryPackItem.BatteryType.BATTERY_LITHIUM, 180_000_000L, 100_000L, 10_000L);
        checkType(helper, BatteryPackItem.BatteryType.BATTERY_SODIUM, 900_000_000L, 500_000L, 50_000L);
        checkType(helper, BatteryPackItem.BatteryType.BATTERY_SCHRABIDIUM, 4_500_000_000L, 2_500_000L, 250_000L);
        checkType(helper, BatteryPackItem.BatteryType.BATTERY_QUANTUM, 72_000_000_000L, 10_000_000L, 1_000_000L);
        checkType(helper, BatteryPackItem.BatteryType.CAPACITOR_COPPER, 600_000L, 1_000L, 1_000L);
        checkType(helper, BatteryPackItem.BatteryType.CAPACITOR_GOLD, 6_000_000L, 10_000L, 10_000L);
        checkType(helper, BatteryPackItem.BatteryType.CAPACITOR_NIOBIUM, 60_000_000L, 100_000L, 100_000L);
        checkType(helper, BatteryPackItem.BatteryType.CAPACITOR_TANTALUM, 300_000_000L, 500_000L, 500_000L);
        checkType(helper, BatteryPackItem.BatteryType.CAPACITOR_BISMUTH, 1_500_000_000L, 2_500_000L, 2_500_000L);
        checkType(helper, BatteryPackItem.BatteryType.CAPACITOR_SPARK, 6_000_000_000L, 10_000_000L, 10_000_000L);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void earlyBatteryRecipesPreserveExactOutputIdentity(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var socket = recipes.byKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "machine_battery_socket_from_red_copper")).orElseThrow().value()
                .getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(socket.is(ModItems.MACHINE_BATTERY_SOCKET_ITEM.get()) && socket.getCount() == 1,
                "Early source socket recipe must output one Battery Socket");

        ItemStack redstone = recipes.byKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "battery_redstone")).orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(redstone.is(ModItems.BATTERY_PACK.get())
                        && BatteryPackItem.type(redstone) == BatteryPackItem.BatteryType.BATTERY_REDSTONE
                        && ModItems.BATTERY_PACK.get().getCharge(redstone) == 0L,
                "Redstone Battery recipe must preserve its empty metadata-era variant");

        ItemStack copper = recipes.byKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "capacitor_copper")).orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(copper.is(ModItems.BATTERY_PACK.get())
                        && BatteryPackItem.type(copper) == BatteryPackItem.BatteryType.CAPACITOR_COPPER
                        && ModItems.BATTERY_PACK.get().getCharge(copper) == 0L,
                "Copper Capacitor recipe must preserve its empty metadata-era variant");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batteryChargeMutationsPreserveVariantAndBounds(GameTestHelper helper) {
        ItemStack stack = BatteryPackItem.create(ModItems.BATTERY_PACK.get(),
                BatteryPackItem.BatteryType.BATTERY_LEAD, false);
        BatteryPackItem item = ModItems.BATTERY_PACK.get();
        item.charge(stack, 500L);
        item.discharge(stack, 125L);
        helper.assertTrue(item.getCharge(stack) == 375L
                        && BatteryPackItem.type(stack) == BatteryPackItem.BatteryType.BATTERY_LEAD,
                "Battery charge mutations must retain the metadata-era variant identity");
        item.setCharge(stack, item.getMaxCharge(stack));
        helper.assertTrue(!item.isBarVisible(stack) && item.getBarWidth(stack) == 13,
                "A full battery must hide its durability bar while retaining a full-width value");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void socketPlacesExactTwoByTwoTopology(GameTestHelper helper) {
        BatterySocketBlockEntity socket = placeSocket(helper, new BlockPos(3, 1, 3));
        BlockPos core = socket.getBlockPos();
        Direction facing = socket.getBlockState().getValue(BatterySocketBlock.FACING);
        BlockPos[] parts = BatterySocketBlock.partPositions(core, facing);
        helper.assertTrue(parts.length == 4, "Battery Socket must expose four port positions");
        for (int i = 0; i < parts.length; i++) {
            helper.assertTrue(helper.getLevel().getBlockState(parts[i]).is(ModBlocks.MACHINE_BATTERY_SOCKET.get()),
                    "Every 2x2 Battery Socket position must contain the shared block identity");
            if (i > 0) {
                helper.assertTrue(helper.getLevel().getBlockEntity(parts[i]) instanceof BatterySocketProxyBlockEntity,
                        "Non-core socket positions must expose inventory and HE connector proxies");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void comparatorRoundsBatteryFillToFifteenSteps(GameTestHelper helper) {
        BatterySocketBlockEntity socket = placeSocket(helper, new BlockPos(3, 1, 3));
        ItemStack stack = BatteryPackItem.create(ModItems.BATTERY_PACK.get(),
                BatteryPackItem.BatteryType.BATTERY_REDSTONE, false);
        socket.setItem(0, stack);
        BatteryPackItem item = ModItems.BATTERY_PACK.get();
        helper.assertTrue(socket.comparatorOutput() == 0, "An empty socket battery must emit comparator strength zero");
        item.setCharge(stack, item.getMaxCharge(stack) / 2L);
        helper.assertTrue(socket.comparatorOutput() == 8,
                "Half charge must round 7.5 upward to comparator strength eight");
        item.setCharge(stack, item.getMaxCharge(stack));
        helper.assertTrue(socket.comparatorOutput() == 15, "A full socket battery must emit comparator strength fifteen");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void redstoneSelectsIndependentHighMode(GameTestHelper helper) {
        BatterySocketBlockEntity socket = placeSocket(helper, new BlockPos(3, 1, 3));
        helper.assertTrue(socket.relevantMode() == BatterySocketBlockEntity.MODE_INPUT,
                "Unpowered Battery Socket mode must default to input");
        helper.getLevel().setBlock(socket.getBlockPos().above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(socket.relevantMode() == BatterySocketBlockEntity.MODE_OUTPUT,
                "A signal on any socket port must select the default output high mode");
        socket.cycleHighMode();
        helper.assertTrue(socket.relevantMode() == BatterySocketBlockEntity.MODE_NONE,
                "The high-mode control must cycle output to source mode three/off");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void automationPreservesFullOnlyExtractionBug(GameTestHelper helper) {
        BatterySocketBlockEntity socket = placeSocket(helper, new BlockPos(3, 1, 3));
        ItemStack stack = BatteryPackItem.create(ModItems.BATTERY_PACK.get(),
                BatteryPackItem.BatteryType.BATTERY_REDSTONE, false);
        socket.setItem(0, stack);
        helper.assertTrue(!socket.canTakeItemThroughFace(0, stack, Direction.UP),
                "The source slot/mode mixup must prevent extracting an empty battery");
        ModItems.BATTERY_PACK.get().setCharge(stack, ModItems.BATTERY_PACK.get().getMaxCharge(stack));
        helper.assertTrue(socket.canTakeItemThroughFace(0, stack, Direction.UP),
                "The source slot/mode mixup must still allow extracting a full battery from slot zero");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "battery_socket_drop_isolated")
    public static void breakingAnyPartDropsBatteryOnlyOnce(GameTestHelper helper) {
        BatterySocketBlockEntity socket = placeSocket(helper, new BlockPos(3, 1, 3));
        ItemStack battery = BatteryPackItem.create(ModItems.BATTERY_PACK.get(),
                BatteryPackItem.BatteryType.BATTERY_LEAD, true);
        socket.setItem(0, battery);
        BlockPos[] parts = BatterySocketBlock.partPositions(socket.getBlockPos(), Direction.NORTH);
        helper.getLevel().destroyBlock(parts[3], true);

        int batteryDrops = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(socket.getBlockPos()).inflate(4.0D))) {
            if (entity.getItem().is(ModItems.BATTERY_PACK.get())) batteryDrops += entity.getItem().getCount();
        }
        helper.assertTrue(batteryDrops == 1,
                "Breaking a proxy must dismantle the multiblock without duplicating its battery inventory");
        for (BlockPos part : parts) {
            helper.assertTrue(helper.getLevel().getBlockState(part).isAir(),
                    "Breaking any Battery Socket part must remove all four positions");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void socketFeedsExistingCableAndShredderNetwork(GameTestHelper helper) {
        BatterySocketBlockEntity socket = placeSocket(helper, new BlockPos(4, 1, 4));
        ItemStack battery = BatteryPackItem.create(ModItems.BATTERY_PACK.get(),
                BatteryPackItem.BatteryType.BATTERY_REDSTONE, true);
        socket.setItem(0, battery);
        socket.cycleLowMode();
        socket.cycleLowMode();

        BlockPos cablePosition = socket.getBlockPos().relative(Direction.NORTH);
        BlockPos shredderPosition = cablePosition.relative(Direction.NORTH);
        helper.getLevel().setBlock(cablePosition, ModBlocks.RED_CABLE.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(shredderPosition, ModBlocks.MACHINE_SHREDDER.get().defaultBlockState(), 3);
        HeCableBlockEntity cable = (HeCableBlockEntity) helper.getLevel().getBlockEntity(cablePosition);
        MachineShredderBlockEntity shredder = (MachineShredderBlockEntity) helper.getLevel().getBlockEntity(shredderPosition);

        HeCableBlockEntity.serverTick(helper.getLevel(), cablePosition, cable.getBlockState(), cable);
        BatterySocketBlockEntity.tick(helper.getLevel(), socket.getBlockPos(), socket.getBlockState(), socket);
        MachineShredderBlockEntity.tick(helper.getLevel(), shredderPosition, shredder.getBlockState(), shredder);
        HeNetworkManager.get(helper.getLevel()).tick();
        BatterySocketBlockEntity.tick(helper.getLevel(), socket.getBlockPos(), socket.getBlockState(), socket);
        MachineShredderBlockEntity.tick(helper.getLevel(), shredderPosition, shredder.getBlockState(), shredder);
        HeNetworkManager.get(helper.getLevel()).tick();

        helper.assertTrue(shredder.getPower() == 100L,
                "The Redstone Battery must feed the Shredder at its exact 100 HE/t discharge rate");
        helper.assertTrue(ModItems.BATTERY_PACK.get().getCharge(battery)
                        == ModItems.BATTERY_PACK.get().getMaxCharge(battery) - 100L,
                "Network delivery must remove the same 100 HE from the finite battery");
        helper.succeed();
    }

    private static BatterySocketBlockEntity placeSocket(GameTestHelper helper, BlockPos relativeCore) {
        var block = ModBlocks.MACHINE_BATTERY_SOCKET.get();
        var state = block.defaultBlockState().setValue(BatterySocketBlock.FACING, Direction.NORTH);
        helper.setBlock(relativeCore, state);
        BlockPos absoluteCore = helper.absolutePos(relativeCore);
        block.setPlacedBy(helper.getLevel(), absoluteCore, state,
                helper.makeMockPlayer(GameType.SURVIVAL), new ItemStack(ModItems.MACHINE_BATTERY_SOCKET_ITEM.get()));
        return (BatterySocketBlockEntity) helper.getLevel().getBlockEntity(absoluteCore);
    }

    private static void checkType(GameTestHelper helper, BatteryPackItem.BatteryType type,
                                  long capacity, long chargeRate, long dischargeRate) {
        ItemStack stack = BatteryPackItem.create(ModItems.BATTERY_PACK.get(), type, true);
        BatteryPackItem item = ModItems.BATTERY_PACK.get();
        helper.assertTrue(item.getMaxCharge(stack) == capacity
                        && item.getChargeRate(stack) == chargeRate
                        && item.getDischargeRate(stack) == dischargeRate
                        && item.getCharge(stack) == capacity,
                type.id() + " must retain its source capacity and transfer rates");
    }
}
