package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.TurbofanBlock;
import com.hbm.ntm.blockentity.TurbofanBlockEntity;
import com.hbm.ntm.blockentity.TurbofanProxyBlockEntity;
import com.hbm.ntm.compat.TurbofanVehiclePhysics;
import com.hbm.ntm.fluid.FluidTankProperties;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.item.DenseWireItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.FoundryMoldItem;
import com.hbm.ntm.item.PipeItem;
import com.hbm.ntm.item.ShellItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TurbofanGameTests {
    private TurbofanGameTests() { }

    @GameTest(template = "empty")
    public static void structureAndFourOutwardComboPortsMatchSource(GameTestHelper helper) {
        BlockPos core = new BlockPos(8, 2, 8);
        List<BlockPos> parts = TurbofanBlock.partPositions(core, Direction.SOUTH);
        check(helper, parts.size() == 63 && new HashSet<>(parts).size() == 63,
                "Turbofan must occupy an exact seven by three by three volume");

        List<TurbofanBlock.Connection> connections = TurbofanBlock.connections(core, Direction.SOUTH);
        check(helper, connections.size() == 4,
                "Source Turbofan must expose exactly four combined HE/fluid ports");
        for (TurbofanBlock.Connection connection : connections) {
            check(helper, connection.port().getY() == core.getY()
                            && connection.target().equals(connection.port().relative(connection.outward()))
                            && (connection.outward() == Direction.SOUTH
                            || connection.outward() == Direction.NORTH),
                    "Every Turbofan target must be immediately outside a lower end proxy");
        }

        helper.setBlock(core, coreState(Direction.SOUTH));
        TurbofanBlockEntity turbofan = helper.getBlockEntity(core);
        check(helper, turbofan.canPlaceItem(TurbofanBlockEntity.FUEL_INPUT, new ItemStack(Items.DIRT))
                        && turbofan.canPlaceItem(TurbofanBlockEntity.AFTERBURNER, new ItemStack(Items.DIRT))
                        && turbofan.canPlaceItem(TurbofanBlockEntity.BATTERY, new ItemStack(Items.DIRT))
                        && turbofan.canPlaceItem(TurbofanBlockEntity.IDENTIFIER, new ItemStack(Items.DIRT))
                        && !turbofan.canPlaceItem(TurbofanBlockEntity.CONTAINER_OUTPUT, new ItemStack(Items.DIRT)),
                "The four ordinary source GUI slots must remain permissive and output take-only");
        BlockPos port = connections.getFirst().port();
        TurbofanBlock block = ModBlocks.MACHINE_TURBOFAN.get();
        helper.setBlock(port, block.stateForPart(port, core, Direction.SOUTH));
        TurbofanProxyBlockEntity proxy = helper.getBlockEntity(port);
        Direction outward = connections.getFirst().outward();
        check(helper, proxy.fluidHandler(outward) != null
                        && proxy.fluidHandler(outward.getOpposite()) == null
                        && proxy.canConnect(outward) && !proxy.canConnect(outward.getOpposite()),
                "Each combo proxy must expose both networks on its outward face only");

        IFluidHandler fluids = proxy.fluidHandler(outward);
        check(helper, fluids != null && fluids.getTanks() == 3
                        && fluids.fill(new FluidStack(ModFluids.KEROSENE.get(), 10),
                        IFluidHandler.FluidAction.EXECUTE) == 10
                        && fluids.fill(new FluidStack(ModFluids.DIESEL.get(), 10),
                        IFluidHandler.FluidAction.SIMULATE) == 0,
                "Port tank zero must be selected-fuel input only");
        turbofan.addBloodForTest(8);
        turbofan.addSmokeForTest(6);
        FluidStack blood = fluids.drain(new FluidStack(ModFluids.BLOOD.get(), 5),
                IFluidHandler.FluidAction.EXECUTE);
        FluidStack smoke = fluids.drain(new FluidStack(ModFluids.SMOKE.get(), 4),
                IFluidHandler.FluidAction.EXECUTE);
        check(helper, blood.is(ModFluids.BLOOD.get()) && blood.getAmount() == 5
                        && smoke.is(ModFluids.SMOKE.get()) && smoke.getAmount() == 4,
                "Port tanks one and two must expose Blood and Smoke as output only");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactAviationAfterburnerPonyAndRedstoneBehavior(GameTestHelper helper) {
        BlockPos core = new BlockPos(8, 2, 8);
        TurbofanBlockEntity turbofan = placeCore(helper, core, Direction.SOUTH);

        turbofan.setSelectedForTest(FluidIdentifierItem.Selection.KEROSENE);
        turbofan.addFuelForTest(1);
        tick(helper, turbofan);
        check(helper, turbofan.wasOn() && turbofan.consumption() == 1
                        && turbofan.output() == 3_850 && turbofan.getPower() == 3_850L,
                "One mB Kerosene must produce the exact source 3,850 HE raw aviation value");

        turbofan.setPower(0L);
        turbofan.setItem(TurbofanBlockEntity.AFTERBURNER,
                new ItemStack(ModItems.MACHINE_UPGRADES.get("upgrade_afterburn_1").get()));
        turbofan.addFuelForTest(2);
        tick(helper, turbofan);
        check(helper, turbofan.afterburner() == 1 && turbofan.consumption() == 2
                        && turbofan.output() == 10_266 && turbofan.getPower() == 10_266L,
                "Level-one Afterburn must consume two mB and truncate to exactly 10,266 HE");

        turbofan.setPower(0L);
        turbofan.setItem(TurbofanBlockEntity.AFTERBURNER,
                new ItemStack(ModItems.MACHINE_UPGRADES.get("upgrade_afterburn_2").get()));
        turbofan.addFuelForTest(3);
        tick(helper, turbofan);
        check(helper, turbofan.afterburner() == 2 && turbofan.consumption() == 3
                        && turbofan.output() == 19_250 && turbofan.getPower() == 19_250L,
                "Level-two Afterburn must consume three mB and produce exactly 19,250 HE");

        turbofan.setPower(0L);
        turbofan.setSelectedForTest(FluidIdentifierItem.Selection.KEROSENE);
        turbofan.setItem(TurbofanBlockEntity.AFTERBURNER,
                new ItemStack(ModItems.MACHINE_UPGRADES.get("upgrade_afterburn_3").get()));
        turbofan.addFuelForTest(4);
        tick(helper, turbofan);
        check(helper, turbofan.afterburner() == 3 && turbofan.consumption() == 4
                        && turbofan.output() == 30_800 && turbofan.getPower() == 30_800L,
                "Level-three Afterburn must consume four mB and produce exactly 30,800 HE");

        turbofan.setPower(0L);
        turbofan.setSelectedForTest(FluidIdentifierItem.Selection.KEROSENE);
        turbofan.setItem(TurbofanBlockEntity.AFTERBURNER, new ItemStack(ModItems.FLAME_PONY.get()));
        turbofan.addFuelForTest(101);
        tick(helper, turbofan);
        check(helper, turbofan.afterburner() == 100 && turbofan.consumption() == 101
                        && turbofan.output() == 1_944_250 && turbofan.getPower() == TurbofanBlockEntity.MAX_POWER,
                "The source pony catalyst must retain its 101 mB, 1,944,250 HE overdrive and final cap");

        turbofan.setPower(0L);
        turbofan.setItem(TurbofanBlockEntity.AFTERBURNER, ItemStack.EMPTY);
        turbofan.setSelectedForTest(FluidIdentifierItem.Selection.DIESEL);
        turbofan.addFuelForTest(1);
        tick(helper, turbofan);
        check(helper, turbofan.wasOn() && turbofan.consumption() == 1
                        && turbofan.output() == 0 && turbofan.getPower() == 0L,
                "Wrong-grade selected fluid must still be wasted and spin the rotor without producing HE");

        turbofan.setSelectedForTest(FluidIdentifierItem.Selection.KEROSENE);
        turbofan.addFuelForTest(1);
        BlockPos target = TurbofanBlock.connections(core, Direction.SOUTH).getFirst().target();
        helper.setBlock(target.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        tick(helper, turbofan);
        check(helper, turbofan.fuelAmount() == 1 && !turbofan.wasOn()
                        && turbofan.consumption() == 0 && turbofan.output() == 0,
                "A signal beside any source combo port must stop fuel burn and all output");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runtimePacketSynchronizesRotorRunningState(GameTestHelper helper) {
        TurbofanBlockEntity serverTurbofan = placeCore(helper, new BlockPos(3, 2, 3), Direction.SOUTH);
        serverTurbofan.setSelectedForTest(FluidIdentifierItem.Selection.KEROSENE);
        serverTurbofan.addFuelForTest(1);
        tick(helper, serverTurbofan);
        check(helper, serverTurbofan.wasOn() && serverTurbofan.output() == 3_850,
                "Server Turbofan must burn Kerosene before synchronizing its rotor state");

        TurbofanBlockEntity clientTurbofan = placeCore(helper, new BlockPos(12, 2, 3), Direction.SOUTH);
        clientTurbofan.onDataPacket(null, ClientboundBlockEntityDataPacket.create(serverTurbofan),
                helper.getLevel().registryAccess());
        check(helper, clientTurbofan.wasOn() && clientTurbofan.output() == 3_850
                        && clientTurbofan.consumption() == 1,
                "Runtime block-entity packets must start the client rotor when Kerosene burns");

        tick(helper, serverTurbofan);
        clientTurbofan.onDataPacket(null, ClientboundBlockEntityDataPacket.create(serverTurbofan),
                helper.getLevel().registryAccess());
        check(helper, !clientTurbofan.wasOn() && clientTurbofan.output() == 0
                        && clientTurbofan.consumption() == 0,
                "Runtime block-entity packets must stop the client rotor after fuel runs out");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sableVehicleThrustTracksRealCombustionOutput(GameTestHelper helper) {
        check(helper, TurbofanVehiclePhysics.exhaustDirection(Direction.NORTH) == Direction.WEST
                        && TurbofanVehiclePhysics.exhaustDirection(Direction.NORTH).getOpposite()
                        == Direction.EAST,
                "Sable exhaust and reaction thrust must follow the model's real west/east rotor axis");
        check(helper, closeEnough(TurbofanVehiclePhysics.thrust(3_850), 256.0D)
                        && closeEnough(TurbofanVehiclePhysics.airflow(3_850), 25.6D),
                "Normal combustion must match one max-speed Aeronautics small propeller");
        check(helper, closeEnough(TurbofanVehiclePhysics.thrust(30_800), 2_048.0D)
                        && closeEnough(TurbofanVehiclePhysics.airflow(30_800),
                        25.6D * Math.sqrt(8.0D)),
                "Afterburner III must scale vehicle thrust from the exact eightfold HE output");
        check(helper, closeEnough(TurbofanVehiclePhysics.thrust(1_944_250), 129_280.0D)
                        && TurbofanVehiclePhysics.isActive(true, 3_850, 1)
                        && !TurbofanVehiclePhysics.isActive(true, 0, 1)
                        && !TurbofanVehiclePhysics.isActive(false, 3_850, 1),
                "Flame Pony must stay absurd while wrong fuel and stopped combustion produce no thrust");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void titaniumShellBloodAndConstructionRecipeRetainSourceIdentity(GameTestHelper helper) {
        ItemStack shell = ShellItem.titanium(ModItems.SHELL.get(), 8);
        CustomModelData model = shell.get(DataComponents.CUSTOM_MODEL_DATA);
        FoundryMaterial.MaterialAmount recovered = FoundryMaterial.fromItem(shell);
        ItemStack cast = FoundryMaterial.TITANIUM.output(FoundryMoldItem.Mold.SHELL);
        check(helper, ShellItem.material(shell) == ShellItem.ShellMaterial.TITANIUM
                        && model != null && model.value() == 2_200
                        && recovered != null && recovered.material() == FoundryMaterial.TITANIUM
                        && recovered.amount() == FoundryMaterial.SHELL
                        && ShellItem.isTitanium(cast),
                "Titanium Shell must retain source metadata 2200 and four-ingot foundry recovery/casting");

        check(helper, FluidIdentifierItem.Selection.fromFluid(ModFluids.BLOOD.get())
                        == FluidIdentifierItem.Selection.BLOOD
                        && UniversalFluidTankItem.ContainedFluid.fromFluid(ModFluids.BLOOD.get())
                        == UniversalFluidTankItem.ContainedFluid.BLOOD
                        && FluidTankProperties.get(FluidIdentifierItem.Selection.BLOOD).liquid()
                        && FluidTankProperties.get(FluidIdentifierItem.Selection.BLOOD).health() == 0,
                "Blood must be a selectable, container-compatible, non-hazardous viscous liquid");

        AssemblyRecipe recipe = AssemblyRecipes.byName("ass.turbofan");
        check(helper, recipe != null && recipe.duration() == 300 && recipe.power() == 100
                        && recipe.output().is(ModItems.MACHINE_TURBOFAN_ITEM.get())
                        && recipe.inputs().size() == 6,
                "ass.turbofan must remain a six-lane 300-tick, 100 HE/t operation");
        check(helper, recipe.inputs().get(0).matches(shell)
                        && recipe.inputs().get(1).matches(PipeItem.duraSteel(ModItems.PIPE.get(), 4))
                        && recipe.inputs().get(2).matches(new ItemStack(ModItems.get("plate_polymer").get(), 12))
                        && recipe.inputs().get(3).matches(new ItemStack(ModItems.TURBINE_TUNGSTEN.get()))
                        && recipe.inputs().get(4).matches(DenseWireItem.create(
                        ModItems.WIRE_DENSE.get(), FoundryMaterial.GOLD, 12))
                        && recipe.inputs().get(5).matches(CircuitItem.create(
                        ModItems.CIRCUIT.get(), CircuitItem.CircuitType.BASIC, 3)),
                "Construction must keep eight Titanium Shells, four Dura pipes, twelve Polymer Plates, "
                        + "one reinforced rotor, twelve dense Gold wires and three Basic Circuits");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reinforcedRotorPonyAndPersistentTanksMatchSource(GameTestHelper helper) {
        CraftingInput rotorInput = CraftingInput.of(3, 3, List.of(
                new ItemStack(ModItems.BLADE_TUNGSTEN.get()), new ItemStack(ModItems.BLADE_TUNGSTEN.get()),
                new ItemStack(ModItems.BLADE_TUNGSTEN.get()), new ItemStack(ModItems.BLADE_TUNGSTEN.get()),
                new ItemStack(ModItems.get("ingot_dura_steel").get()),
                new ItemStack(ModItems.BLADE_TUNGSTEN.get()), new ItemStack(ModItems.BLADE_TUNGSTEN.get()),
                new ItemStack(ModItems.BLADE_TUNGSTEN.get()), new ItemStack(ModItems.BLADE_TUNGSTEN.get())));
        checkCrafts(helper, rotorInput, ModItems.TURBINE_TUNGSTEN.get(),
                "Eight Tungsten Blades around one Dura-Steel ingot must craft the reinforced rotor");

        CraftingInput ponyInput = CraftingInput.of(3, 3, List.of(
                ItemStack.EMPTY, new ItemStack(Items.YELLOW_DYE), ItemStack.EMPTY,
                new ItemStack(Items.PINK_DYE), new ItemStack(Items.PAPER), new ItemStack(Items.PINK_DYE),
                ItemStack.EMPTY, new ItemStack(Items.YELLOW_DYE), ItemStack.EMPTY));
        checkCrafts(helper, ponyInput, ModItems.FLAME_PONY.get(),
                "Yellow/Pink dye around Paper must preserve the source Flame Pony recipe");

        BlockPos core = new BlockPos(5, 2, 5);
        TurbofanBlockEntity turbofan = placeCore(helper, core, Direction.EAST);
        turbofan.setSelectedForTest(FluidIdentifierItem.Selection.KEROSENE);
        turbofan.addFuelForTest(2_345);
        turbofan.addBloodForTest(987);
        turbofan.addSmokeForTest(77);
        turbofan.setPower(12_345L);
        CompoundTag saved = turbofan.saveWithoutMetadata(helper.getLevel().registryAccess());

        TurbofanBlockEntity restored = new TurbofanBlockEntity(
                helper.absolutePos(core.above(4)), coreState(Direction.EAST));
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, restored.selectedFluid() == FluidIdentifierItem.Selection.KEROSENE
                        && restored.fuelAmount() == 2_345 && restored.bloodAmount() == 987
                        && restored.smokeAmount() == 77 && restored.getPower() == 12_345L,
                "Selected fuel, Fuel, Blood, Smoke and powerTime must round-trip through NBT");
        helper.succeed();
    }

    private static void checkCrafts(GameTestHelper helper, CraftingInput input,
                                    net.minecraft.world.item.Item expected, String message) {
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(
                RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        check(helper, recipe.value().assemble(input, helper.getLevel().registryAccess()).is(expected), message);
    }

    private static TurbofanBlockEntity placeCore(GameTestHelper helper, BlockPos position, Direction facing) {
        helper.setBlock(position, coreState(facing));
        return helper.getBlockEntity(position);
    }

    private static BlockState coreState(Direction facing) {
        return ModBlocks.MACHINE_TURBOFAN.get().defaultBlockState()
                .setValue(TurbofanBlock.FACING, facing)
                .setValue(TurbofanBlock.PART_LENGTH, 1)
                .setValue(TurbofanBlock.PART_SIDE, 3)
                .setValue(TurbofanBlock.PART_Y, 0);
    }

    private static void tick(GameTestHelper helper, TurbofanBlockEntity turbofan) {
        TurbofanBlockEntity.tick(helper.getLevel(), turbofan.getBlockPos(),
                turbofan.getBlockState(), turbofan);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    private static boolean closeEnough(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000_001D;
    }
}
