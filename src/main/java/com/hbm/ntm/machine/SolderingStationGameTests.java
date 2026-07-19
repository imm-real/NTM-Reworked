package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.SolderingStationBlock;
import com.hbm.ntm.blockentity.SolderingStationBlockEntity;
import com.hbm.ntm.blockentity.SolderingStationProxyBlockEntity;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.WireFineItem;
import com.hbm.ntm.recipe.SolderingRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SolderingStationGameTests {
    private SolderingStationGameTests() { }

    @GameTest(template = "empty")
    public static void stationUsesFourBlockFootprintAndThreeProxies(GameTestHelper helper) {
        SolderingStationBlockEntity station = placeStation(helper, new BlockPos(3, 1, 3));
        Direction facing = station.getBlockState().getValue(SolderingStationBlock.FACING);
        BlockPos[] parts = SolderingStationBlock.partPositions(station.getBlockPos(), facing);
        Direction side = facing.getClockWise();
        check(helper, parts[1].equals(station.getBlockPos().relative(facing.getOpposite()))
                        && parts[2].equals(station.getBlockPos().relative(side))
                        && parts[3].equals(station.getBlockPos().relative(facing.getOpposite()).relative(side)),
                "The source footprint must extend opposite the facing and clockwise from the core");
        int proxies = 0;
        for (BlockPos part : parts) {
            check(helper, helper.getLevel().getBlockState(part).is(ModBlocks.MACHINE_SOLDERING_STATION.get()),
                    "Every Soldering Station footprint cell must use the shared block identity");
            check(helper, SolderingStationBlock.corePosition(part, helper.getLevel().getBlockState(part))
                            .equals(station.getBlockPos()),
                    "Every Soldering Station footprint cell must resolve to the visible model's core");
            if (helper.getLevel().getBlockEntity(part) instanceof SolderingStationProxyBlockEntity) proxies++;
        }
        check(helper, parts.length == 4 && proxies == 3,
                "Soldering Station must be a one-layer 2x2 with three capability proxies");
        var coreItems = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                station.getBlockPos(), Direction.NORTH);
        var coreFluid = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                station.getBlockPos(), Direction.NORTH);
        check(helper, coreItems != null && coreFluid != null,
                "The core corner must expose item and fluid capabilities like the source machine");
        check(helper, coreFluid.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE) == 0,
                "Without a fluid identifier, the Soldering tank must reject arbitrary external fluid");
        helper.getLevel().destroyBlock(parts[3], false);
        for (BlockPos part : parts) check(helper, helper.getLevel().getBlockState(part).isAir(),
                "Breaking any Soldering Station part must dismantle the full footprint");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactAnalogAndBasicRecipesMatchTheirThreeCategories(GameTestHelper helper) {
        ItemStack[] analog = new ItemStack[11];
        Arrays.fill(analog, ItemStack.EMPTY);
        loadAnalog(analog);
        analog[0].set(DataComponents.CUSTOM_NAME, Component.literal("matched tube lot"));
        analog[5].set(DataComponents.CUSTOM_NAME, Component.literal("matched solder lot"));
        SolderingRecipes.SolderingRecipe analogRecipe = SolderingRecipes.find(analog);
        check(helper, analogRecipe != null && analogRecipe.duration() == 100 && analogRecipe.consumption() == 100L
                        && CircuitItem.type(analogRecipe.output()) == CircuitItem.CircuitType.ANALOG,
                "Analog must be 3 Vacuum Tubes, 2 Capacitors, 4 PCBs, 4 Lead Wires for 100 ticks at 100 HE/t");

        ItemStack[] basic = new ItemStack[11];
        Arrays.fill(basic, ItemStack.EMPTY);
        loadBasic(basic);
        SolderingRecipes.SolderingRecipe basicRecipe = SolderingRecipes.find(basic);
        check(helper, basicRecipe != null && basicRecipe.duration() == 200 && basicRecipe.consumption() == 250L
                        && CircuitItem.type(basicRecipe.output()) == CircuitItem.CircuitType.BASIC,
                "Basic must be 4 Microchips, 4 PCBs, 4 Lead Wires for 200 ticks at 250 HE/t");
        basic[1] = CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CHIP, 1);
        check(helper, SolderingRecipes.find(basic) == null,
                "Extra or split category inputs must reject Soldering recipe matching");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void analogCompletesInOneHundredTicksForTenThousandHe(GameTestHelper helper) {
        SolderingStationBlockEntity station = bareStation(helper, new BlockPos(3, 1, 3));
        loadAnalog(station);
        station.setPower(10_000L);
        for (int i = 0; i < 99; i++) tick(helper, station);
        check(helper, station.getItem(6).isEmpty() && station.getItem(0).getCount() == 3,
                "Analog inputs must remain intact before tick 100");
        tick(helper, station);
        check(helper, CircuitItem.type(station.getItem(6)) == CircuitItem.CircuitType.ANALOG
                        && station.getItem(6).getCount() == 1,
                "Tick 100 must output one exact Analog Circuit");
        check(helper, station.getPower() == 0L,
                "Analog must consume exactly 10,000 HE");
        check(helper, station.getItem(0).isEmpty() && station.getItem(1).isEmpty()
                        && station.getItem(3).isEmpty() && station.getItem(5).isEmpty(),
                "Analog completion must consume all category inputs atomically");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void basicCompletesInTwoHundredTicksForFiftyThousandHe(GameTestHelper helper) {
        SolderingStationBlockEntity station = bareStation(helper, new BlockPos(3, 1, 3));
        loadBasic(station);
        station.setPower(50_000L);
        for (int i = 0; i < 200; i++) tick(helper, station);
        check(helper, CircuitItem.type(station.getItem(6)) == CircuitItem.CircuitType.BASIC
                        && station.getItem(6).getCount() == 1,
                "Basic recipe must output one exact Basic Circuit after 200 ticks");
        check(helper, station.getPower() == 0L,
                "Basic recipe must consume exactly 50,000 HE");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void advancedCircuitConsumesExactRubberAcidTimeAndEnergy(GameTestHelper helper) {
        SolderingStationBlockEntity station = bareStation(helper, new BlockPos(3, 1, 3));
        loadAdvanced(station);
        station.setItem(SolderingStationBlockEntity.FLUID_IDENTIFIER,
                identifier(FluidIdentifierItem.Selection.SULFURIC_ACID));
        station.tank().fill(new FluidStack(ModFluids.SULFURIC_ACID.get(), 1_000),
                IFluidHandler.FluidAction.EXECUTE);
        station.setPower(300_000L);
        for (int tick = 0; tick < 299; tick++) tick(helper, station);
        check(helper, station.getItem(SolderingStationBlockEntity.OUTPUT).isEmpty()
                        && station.tank().getFluidAmount() == 1_000,
                "Advanced inputs and Acid must remain intact before source tick 300");
        tick(helper, station);
        check(helper, CircuitItem.type(station.getItem(SolderingStationBlockEntity.OUTPUT))
                        == CircuitItem.CircuitType.ADVANCED
                        && station.getPower() == 0L && station.tank().isEmpty()
                        && station.getItem(0).isEmpty() && station.getItem(1).isEmpty()
                        && station.getItem(3).isEmpty() && station.getItem(4).isEmpty()
                        && station.getItem(5).isEmpty(),
                "16 Chips + 4 Capacitors + 8 PCBs + 2 Rubber + 8 Lead Wire + 1,000 mB Acid"
                        + " must yield one Advanced Circuit in 300 ticks at 1,000 HE/t");

        SolderingStationBlockEntity dry = bareStation(helper, new BlockPos(6, 1, 3));
        loadAdvanced(dry);
        dry.setPower(300_000L);
        tick(helper, dry);
        check(helper, dry.progress() == 0 && dry.getPower() == 300_000L,
                "The fluid-bearing Advanced recipe must not consume HE without exact Sulfuric Acid");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batteryPowersFirstSolderingTickAndBlockedOutputRollsBack(GameTestHelper helper) {
        SolderingStationBlockEntity batteryStation = bareStation(helper, new BlockPos(2, 1, 2));
        loadAnalog(batteryStation);
        batteryStation.setItem(7, new ItemStack(ModItems.BATTERY_CREATIVE.get()));
        tick(helper, batteryStation);
        check(helper, batteryStation.progress() == 1 && batteryStation.getPower() == 1_900L,
                "Battery discharge must occur before the first Soldering processing attempt");

        SolderingStationBlockEntity blocked = bareStation(helper, new BlockPos(6, 1, 2));
        loadAnalog(blocked);
        blocked.setPower(10_000L);
        blocked.setItem(6, CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.ANALOG, 64));
        tick(helper, blocked);
        check(helper, blocked.progress() == 0 && blocked.getPower() == 10_000L
                        && blocked.getItem(0).getCount() == 3,
                "Full output must reset Soldering progress without HE or input consumption");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collisionPreventionBlocksDryRecipeWhenTankContainsFluid(GameTestHelper helper) {
        SolderingStationBlockEntity station = bareStation(helper, new BlockPos(3, 1, 3));
        loadAnalog(station);
        station.setPower(10_000L);
        station.setItem(8, identifier(FluidIdentifierItem.Selection.WATER));
        station.tank().fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);
        station.toggleCollisionPrevention();
        tick(helper, station);
        check(helper, station.progress() == 0 && station.getPower() == 10_000L,
                "Collision prevention must block no-fluid Analog recipe while any fluid is present");
        station.toggleCollisionPrevention();
        tick(helper, station);
        check(helper, station.progress() == 1 && station.getPower() == 9_900L,
                "Disabling collision prevention must allow the same dry recipe to run");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidIdentifierGatesExactFluidAndRetypingClearsTank(GameTestHelper helper) {
        SolderingStationBlockEntity station = placeStation(helper, new BlockPos(3, 1, 3));
        BlockPos proxy = SolderingStationBlock.partPositions(station.getBlockPos(),
                station.getBlockState().getValue(SolderingStationBlock.FACING))[1];
        IFluidHandler handler = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK, proxy, Direction.NORTH);
        check(helper, handler != null, "Every Soldering proxy must expose the identifier-gated fluid capability");
        check(helper, handler.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE) == 0,
                "A Soldering Station without an identifier must reject fluid");

        ItemStack identifier = identifier(FluidIdentifierItem.Selection.WATER);
        FluidIdentifierItem.set(identifier, FluidIdentifierItem.Selection.LAVA, false);
        ItemStack remainder = identifier.getCraftingRemainingItem();
        check(helper, identifier.hasCraftingRemainingItem()
                        && FluidIdentifierItem.primary(remainder) == FluidIdentifierItem.Selection.WATER
                        && FluidIdentifierItem.secondary(remainder) == FluidIdentifierItem.Selection.LAVA,
                "The reusable identifier must remain in crafting with both source selections intact");
        station.setItem(8, identifier);
        check(helper, handler.fill(new FluidStack(Fluids.LAVA, 250), IFluidHandler.FluidAction.EXECUTE) == 0
                        && handler.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE) == 250,
                "Only the identifier's primary fluid may enter the tank");

        FluidIdentifierItem.swap(identifier);
        tick(helper, station);
        check(helper, station.tank().isEmpty(),
                "Changing the installed primary identifier must clear incompatible tank contents");
        check(helper, handler.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE) == 0
                        && handler.fill(new FluidStack(Fluids.LAVA, 250), IFluidHandler.FluidAction.EXECUTE) == 250,
                "After a source-style primary/secondary swap, only the new primary fluid may enter");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidIdentifierAndTankPersistAndTankSynchronizes(GameTestHelper helper) {
        SolderingStationBlockEntity station = bareStation(helper, new BlockPos(2, 1, 2));
        ItemStack identifier = identifier(FluidIdentifierItem.Selection.WATER);
        FluidIdentifierItem.set(identifier, FluidIdentifierItem.Selection.LAVA, false);
        station.setItem(8, identifier);
        station.fluidHandler().fill(new FluidStack(Fluids.WATER, 750), IFluidHandler.FluidAction.EXECUTE);

        var saved = station.saveWithoutMetadata(helper.getLevel().registryAccess());
        SolderingStationBlockEntity loaded = bareStation(helper, new BlockPos(5, 1, 2));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, FluidIdentifierItem.primary(loaded.getItem(8)) == FluidIdentifierItem.Selection.WATER
                        && FluidIdentifierItem.secondary(loaded.getItem(8)) == FluidIdentifierItem.Selection.LAVA
                        && loaded.tank().getFluidAmount() == 750 && loaded.tank().getFluid().is(Fluids.WATER),
                "Both identifier selections and tank contents must persist");

        SolderingStationBlockEntity clientCopy = bareStation(helper, new BlockPos(7, 1, 2));
        clientCopy.handleUpdateTag(loaded.getUpdateTag(helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        check(helper, clientCopy.tank().getFluidAmount() == 750 && clientCopy.tank().getFluid().is(Fluids.WATER),
                "Soldering tank fluid and amount must be included in client update data");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradeFormulasAndAutomationPreserveSourceRules(GameTestHelper helper) {
        SolderingStationBlockEntity station = bareStation(helper, new BlockPos(3, 1, 3));
        loadAnalog(station);
        station.setItem(9, new ItemStack(ModItems.MACHINE_UPGRADES.get("upgrade_speed_3").get()));
        tick(helper, station);
        check(helper, station.processTime() == 50 && station.consumption() == 400L,
                "Speed III must halve duration and quadruple per-tick consumption");
        station.setItem(9, new ItemStack(ModItems.MACHINE_UPGRADES.get("upgrade_power_3").get()));
        tick(helper, station);
        check(helper, station.processTime() == 200 && station.consumption() == 50L,
                "Power III must double duration and halve consumption by integer source formula");
        station.setItem(9, new ItemStack(ModItems.MACHINE_UPGRADES.get("upgrade_overdrive_3").get()));
        station.setPower(100_000L);
        tick(helper, station);
        check(helper, station.processTime() == 100 && station.consumption() == 800L && station.progress() == 4,
                "Overdrive III must multiply consumption by eight and add four progress per tick");
        check(helper, Arrays.equals(station.getSlotsForFace(Direction.NORTH), new int[]{0,1,2,3,4,5,6})
                        && station.canTakeItemThroughFace(6, ItemStack.EMPTY, Direction.NORTH)
                        && !station.canTakeItemThroughFace(0, station.getItem(0), Direction.NORTH),
                "Automation must expose inputs 0-5 and output 6, extracting output only");
        helper.succeed();
    }

    private static SolderingStationBlockEntity placeStation(GameTestHelper helper, BlockPos relativeCore) {
        SolderingStationBlock block = ModBlocks.MACHINE_SOLDERING_STATION.get();
        var state = block.defaultBlockState().setValue(SolderingStationBlock.FACING, Direction.NORTH);
        helper.setBlock(relativeCore, state);
        BlockPos absolute = helper.absolutePos(relativeCore);
        block.setPlacedBy(helper.getLevel(), absolute, state, helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.MACHINE_SOLDERING_STATION_ITEM.get()));
        return (SolderingStationBlockEntity) helper.getLevel().getBlockEntity(absolute);
    }

    private static SolderingStationBlockEntity bareStation(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_SOLDERING_STATION.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void loadAnalog(SolderingStationBlockEntity station) {
        ItemStack[] stacks = new ItemStack[11]; Arrays.fill(stacks, ItemStack.EMPTY); loadAnalog(stacks);
        for (int i = 0; i <= 5; i++) station.setItem(i, stacks[i]);
    }
    private static void loadAnalog(ItemStack[] stacks) {
        stacks[0] = CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.VACUUM_TUBE, 3);
        stacks[1] = CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CAPACITOR, 2);
        stacks[3] = CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.PCB, 4);
        stacks[5] = WireFineItem.create(ModItems.WIRE_FINE.get(), WireFineItem.WireMaterial.LEAD, 4);
    }
    private static void loadBasic(SolderingStationBlockEntity station) {
        ItemStack[] stacks = new ItemStack[11]; Arrays.fill(stacks, ItemStack.EMPTY); loadBasic(stacks);
        for (int i = 0; i <= 5; i++) station.setItem(i, stacks[i]);
    }
    private static void loadBasic(ItemStack[] stacks) {
        stacks[0] = CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CHIP, 4);
        stacks[3] = CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.PCB, 4);
        stacks[5] = WireFineItem.create(ModItems.WIRE_FINE.get(), WireFineItem.WireMaterial.LEAD, 4);
    }
    private static void loadAdvanced(SolderingStationBlockEntity station) {
        station.setItem(0, CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CHIP, 16));
        station.setItem(1, CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CAPACITOR, 4));
        station.setItem(3, CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.PCB, 8));
        station.setItem(4, new ItemStack(ModItems.get("ingot_rubber").get(), 2));
        station.setItem(5, WireFineItem.create(ModItems.WIRE_FINE.get(), WireFineItem.WireMaterial.LEAD, 8));
    }
    private static ItemStack identifier(FluidIdentifierItem.Selection selection) {
        ItemStack stack = new ItemStack(ModItems.FLUID_IDENTIFIER_MULTI.get());
        FluidIdentifierItem.set(stack, selection, true);
        return stack;
    }
    private static void tick(GameTestHelper helper, SolderingStationBlockEntity station) {
        SolderingStationBlockEntity.tick(helper.getLevel(), station.getBlockPos(), station.getBlockState(), station);
    }
    private static void check(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }
}
