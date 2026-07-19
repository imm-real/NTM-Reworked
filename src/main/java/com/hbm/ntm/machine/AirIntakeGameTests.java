package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.AirIntakeBlock;
import com.hbm.ntm.blockentity.AirIntakeBlockEntity;
import com.hbm.ntm.blockentity.AirIntakeProxyBlockEntity;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirIntakeGameTests {
    private AirIntakeGameTests() { }

    @GameTest(template = "empty")
    public static void sourceFootprintAndEightConnectionsAreExact(GameTestHelper helper) {
        BlockPos core = new BlockPos(5, 2, 5);
        Direction facing = Direction.SOUTH;
        List<BlockPos> parts = AirIntakeBlock.partPositions(core, facing);
        check(helper, parts.size() == 4 && new HashSet<>(parts).size() == 4
                        && parts.containsAll(List.of(core, core.north(), core.west(), core.north().west())),
                "South-facing Air Intake must occupy core, back, clockwise and back-clockwise cells");

        List<AirIntakeBlock.Connection> connections = AirIntakeBlock.connections(core, facing);
        check(helper, connections.size() == 8
                        && new HashSet<>(connections.stream().map(AirIntakeBlock.Connection::target).toList()).size() == 8,
                "Air Intake must preserve all eight source perimeter connection positions");
        check(helper, connections.get(0).target().equals(core.south())
                        && connections.get(2).target().equals(core.north(2))
                        && connections.get(4).target().equals(core.west(2))
                        && connections.get(6).target().equals(core.east()),
                "Air Intake front, rear, clockwise and counter-clockwise connections must not rotate backwards");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void placementCreatesOneCoreAndThreePowerFluidProxies(GameTestHelper helper) {
        BlockPos relativeCore = new BlockPos(5, 2, 5);
        AirIntakeBlock block = ModBlocks.MACHINE_INTAKE.get();
        var state = block.defaultBlockState().setValue(AirIntakeBlock.FACING, Direction.SOUTH);
        helper.setBlock(relativeCore, state);
        BlockPos core = helper.absolutePos(relativeCore);
        block.setPlacedBy(helper.getLevel(), core, state, helper.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(ModItems.MACHINE_INTAKE_ITEM.get()));

        int cores = 0;
        int proxies = 0;
        for (BlockPos part : AirIntakeBlock.partPositions(core, Direction.SOUTH)) {
            if (helper.getLevel().getBlockEntity(part) instanceof AirIntakeBlockEntity) cores++;
            if (helper.getLevel().getBlockEntity(part) instanceof AirIntakeProxyBlockEntity proxy) {
                proxies++;
                check(helper, proxy.canConnect(Direction.NORTH)
                                && proxy.fluidHandler(Direction.NORTH) != null
                                && proxy.fluidHandler(Direction.UP) == null,
                        "Each source dummy must be a horizontal-only power/fluid ProxyCombo");
            }
        }
        check(helper, cores == 1 && proxies == 3,
                "The exact 2x2 Intake must contain one core and three ProxyCombo cells");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneHundredHeRefillsExactAirTankAndPersists(GameTestHelper helper) {
        BlockPos position = new BlockPos(4, 2, 4);
        helper.setBlock(position, ModBlocks.MACHINE_INTAKE.get().defaultBlockState()
                .setValue(AirIntakeBlock.FACING, Direction.SOUTH));
        AirIntakeBlockEntity intake = helper.getBlockEntity(position);
        intake.setPower(200L);
        AirIntakeBlockEntity.tick(helper.getLevel(), helper.absolutePos(position),
                helper.getBlockState(position), intake);
        check(helper, intake.getPower() == 100L && intake.airTank().getFluidAmount() == 1_000
                        && intake.airTank().getFluid().is(ModFluids.AIR.get()) && intake.active(),
                "One source tick must spend 100HE and set the ordinary-Air tank to exactly 1,000mB");

        IFluidHandler output = intake.outputHandler();
        check(helper, output.fill(new FluidStack(ModFluids.AIR.get(), 1),
                        IFluidHandler.FluidAction.EXECUTE) == 0
                        && output.drain(new FluidStack(ModFluids.AIR.get(), 250),
                        IFluidHandler.FluidAction.EXECUTE).getAmount() == 250,
                "Air Intake capability must be sender-only and drain ordinary Air");

        CompoundTag saved = intake.saveWithoutMetadata(helper.getLevel().registryAccess());
        BlockPos loadedPosition = new BlockPos(7, 2, 4);
        helper.setBlock(loadedPosition, ModBlocks.MACHINE_INTAKE.get().defaultBlockState());
        AirIntakeBlockEntity loaded = helper.getBlockEntity(loadedPosition);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.getPower() == 100L && loaded.airTank().getFluidAmount() == 750
                        && loaded.airTank().getFluid().is(ModFluids.AIR.get()),
                "Source power and compair tank must survive NBT round trips");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceCraftingGridUsesGratesSteelMotorAndTank(GameTestHelper helper) {
        ItemStack grate = new ItemStack(ModItems.STEEL_GRATE_ITEM.get());
        ItemStack plate = new ItemStack(ModItems.get("plate_steel").get());
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                grate.copy(), grate.copy(), grate.copy(),
                plate.copy(), new ItemStack(ModItems.MOTOR.get()), plate.copy(),
                plate.copy(), new ItemStack(ModItems.TANK_STEEL.get()), plate.copy()));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                input, helper.getLevel()).orElseThrow();
        check(helper, recipe.value().assemble(input, helper.getLevel().registryAccess())
                        .is(ModItems.MACHINE_INTAKE_ITEM.get()),
                "GGG/PMP/PTP must preserve the source Air Intake crafting recipe");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
