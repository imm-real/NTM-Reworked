package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.anvil.AnvilRecipes;
import com.hbm.ntm.block.FluidBurnerBlock;
import com.hbm.ntm.blockentity.FluidBurnerBlockEntity;
import com.hbm.ntm.blockentity.FluidBurnerProxyBlockEntity;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.PipeItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.recipe.FluidBurnerFuels;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumMap;
import java.util.Map;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidBurnerGameTests {
    private FluidBurnerGameTests() { }

    @GameTest(template = "empty")
    public static void placementBuildsExactEighteenCellsAndFiveFunctionalProxies(GameTestHelper helper) {
        FluidBurnerBlock block = ModBlocks.HEATER_OILBURNER.get();
        BlockPos clicked = new BlockPos(4, 1, 4);
        Direction facing = Direction.SOUTH;
        BlockPos core = clicked.relative(facing.getOpposite());
        BlockState placedState = block.stateForPart(clicked, core, facing);
        helper.setBlock(clicked, placedState);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(clicked), placedState,
                helper.makeMockPlayer(GameType.SURVIVAL), new ItemStack(ModItems.HEATER_OILBURNER_ITEM.get()));

        int cells = 0;
        int cores = 0;
        int fluidPorts = 0;
        int heatPorts = 0;
        int inert = 0;
        for (BlockPos part : FluidBurnerBlock.partPositions(core)) {
            BlockState state = helper.getBlockState(part);
            check(helper, state.is(block), "Every source 3x2x3 Fluid Burner cell must be present");
            check(helper, FluidBurnerBlock.corePosition(part, state).equals(core),
                    "Every Burner part must resolve to the same core");
            BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(part));
            if (entity instanceof FluidBurnerBlockEntity) cores++;
            else if (entity instanceof FluidBurnerProxyBlockEntity proxy) {
                if (FluidBurnerBlock.isFluidPort(state)) {
                    fluidPorts++;
                    check(helper, proxy.fluidHandler() != null && proxy.getHeatStored() == 0,
                            "Each lower cardinal source proxy must expose fluid but not heat");
                } else if (FluidBurnerBlock.isHeatPort(state)) {
                    heatPorts++;
                    check(helper, proxy.fluidHandler() == null,
                            "The source top-center proxy must expose heat but not fluid");
                }
            } else inert++;
            cells++;
        }
        check(helper, cells == 18 && cores == 1 && fluidPorts == 4 && heatPorts == 1 && inert == 12,
                "The Burner must have 18 cells, one core, four fluid ports, one heat port and 12 dummies");

        helper.destroyBlock(core.offset(1, 1, 1));
        for (BlockPos part : FluidBurnerBlock.partPositions(core)) {
            check(helper, !helper.getBlockState(part).is(block),
                    "Breaking any Burner cell must dismantle the whole source structure");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactSourceFluidHeatTableAndPollutionFlagsArePreserved(GameTestHelper helper) {
        Map<FluidIdentifierItem.Selection, Integer> expected = new EnumMap<>(FluidIdentifierItem.Selection.class);
        expected.put(FluidIdentifierItem.Selection.OIL, 10);
        expected.put(FluidIdentifierItem.Selection.HEAVYOIL, 50);
        expected.put(FluidIdentifierItem.Selection.SMEAR, 50);
        expected.put(FluidIdentifierItem.Selection.HEATINGOIL, 150);
        expected.put(FluidIdentifierItem.Selection.WOODOIL, 110);
        expected.put(FluidIdentifierItem.Selection.COALCREOSOTE, 250);
        expected.put(FluidIdentifierItem.Selection.NAPHTHA, 125);
        expected.put(FluidIdentifierItem.Selection.DIESEL, 200);
        expected.put(FluidIdentifierItem.Selection.LIGHTOIL, 200);
        expected.put(FluidIdentifierItem.Selection.KEROSENE, 300);
        expected.put(FluidIdentifierItem.Selection.GAS, 10);
        expected.put(FluidIdentifierItem.Selection.PETROLEUM, 25);
        expected.put(FluidIdentifierItem.Selection.HYDROGEN, 5);
        expected.put(FluidIdentifierItem.Selection.UNSATURATEDS, 1_000);
        expected.put(FluidIdentifierItem.Selection.FLUE, 25);
        for (FluidIdentifierItem.Selection selection : FluidIdentifierItem.Selection.values()) {
            int heat = expected.getOrDefault(selection, 0);
            check(helper, FluidBurnerFuels.heatPerMb(selection) == heat
                            && FluidBurnerFuels.flammable(selection) == (heat > 0),
                    "Every registered fluid must retain its exact source FT_Flammable value");
            check(helper, FluidBurnerFuels.polluting(selection)
                            == (heat > 0 && selection != FluidIdentifierItem.Selection.HYDROGEN),
                    "Hydrogen alone must be the nonpolluting flammable source fuel");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void settingBurnRateAndHeatGenerationMatchSource(GameTestHelper helper) {
        FluidBurnerBlockEntity burner = bareBurner(helper, new BlockPos(3, 1, 3));
        burner.addFuelForTest(1_000);
        burner.toggleSetting();
        burner.toggleSetting();
        burner.toggleSetting();
        burner.toggleOn();

        tick(helper, burner);
        check(helper, burner.setting() == 4 && burner.fuelAmount() == 996
                        && burner.heatEnergy() == 600 && burner.isOn(),
                "Setting four must burn four mB Heating Oil and generate exactly 600 TU in one tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overshootFullFreezeEmptyFuelAndCoolingQuirksMatchSource(GameTestHelper helper) {
        FluidBurnerBlockEntity overshoot = bareBurner(helper, new BlockPos(2, 1, 2));
        loadState(helper, overshoot, FluidIdentifierItem.Selection.UNSATURATEDS, 2, 99_999, true, 2);
        tick(helper, overshoot);
        check(helper, overshoot.heatEnergy() == 101_999 && overshoot.fuelAmount() == 0,
                "The final burn must overshoot the 100,000 TU cap without clamping");
        tick(helper, overshoot);
        overshoot.toggleOn();
        tick(helper, overshoot);
        check(helper, overshoot.heatEnergy() == 101_999,
                "At or above the cap the source Burner must neither burn nor cool, even when switched off");

        FluidBurnerBlockEntity emptyOn = bareBurner(helper, new BlockPos(5, 1, 2));
        loadState(helper, emptyOn, FluidIdentifierItem.Selection.HEATINGOIL, 0, 1_000, true, 1);
        tick(helper, emptyOn);
        check(helper, emptyOn.heatEnergy() == 1_000,
                "An enabled empty tank selected to a flammable fluid must suppress cooling");

        FluidBurnerBlockEntity cooling = bareBurner(helper, new BlockPos(7, 1, 2));
        loadState(helper, cooling, FluidIdentifierItem.Selection.HEATINGOIL, 0, 1_000, false, 1);
        tick(helper, cooling);
        check(helper, cooling.heatEnergy() == 999,
                "An off Burner below capacity must cool by max(heat / 1000, 1)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void containerLoadingSelectorClearingAndNbtMatchSourceOrder(GameTestHelper helper) {
        FluidBurnerBlockEntity burner = bareBurner(helper, new BlockPos(3, 1, 3));
        burner.setItem(0, UniversalFluidTankItem.create(ModItems.FLUID_TANK_FULL.get(),
                UniversalFluidTankItem.ContainedFluid.HEATINGOIL, 2));
        tick(helper, burner);
        check(helper, burner.fuelAmount() == 1_000 && burner.getItem(0).getCount() == 1
                        && burner.getItem(1).is(ModItems.FLUID_TANK_EMPTY.get()),
                "A matching source tank must load 1,000 mB and move its empty tank to output");

        ItemStack identifier = new ItemStack(ModItems.FLUID_IDENTIFIER_MULTI.get());
        FluidIdentifierItem.set(identifier, FluidIdentifierItem.Selection.DIESEL, true);
        burner.setItem(2, identifier);
        tick(helper, burner);
        check(helper, burner.selectedFluid() == FluidIdentifierItem.Selection.DIESEL
                        && burner.fuelAmount() == 0,
                "The source tick order must load first, then switch identifier type and clear the tank");

        loadState(helper, burner, FluidIdentifierItem.Selection.KEROSENE, 321, 45_678, true, 7);
        CompoundTag saved = burner.saveWithoutMetadata(helper.getLevel().registryAccess());
        FluidBurnerBlockEntity loaded = bareBurner(helper, new BlockPos(6, 1, 6));
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, loaded.selectedFluid() == FluidIdentifierItem.Selection.KEROSENE
                        && loaded.fuelAmount() == 321 && loaded.heatEnergy() == 45_678
                        && loaded.isOn() && loaded.setting() == 7,
                "Selected fluid, tank, heat, on-state and setting must persist with source-compatible keys");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tierTwoConstructionUsesExactSourceIdentities(GameTestHelper helper) {
        AnvilRecipes.Construction recipe = AnvilRecipes.byId(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "anvil/heater_oilburner"));
        check(helper, recipe != null && recipe.tierLower() == 2 && recipe.inputs().size() == 4
                        && recipe.icon().is(ModItems.HEATER_OILBURNER_ITEM.get()),
                "The Fluid Burner must remain a four-input Tier 2 construction operation");
        check(helper, recipe.inputs().get(0).count() == 4
                        && recipe.inputs().get(0).matches(new ItemStack(ModItems.TANK_STEEL.get()))
                        && recipe.inputs().get(1).count() == 3
                        && recipe.inputs().get(1).matches(PipeItem.steel(ModItems.PIPE.get(), 1))
                        && !recipe.inputs().get(1).matches(PipeItem.copper(ModItems.PIPE.get(), 1))
                        && recipe.inputs().get(2).count() == 12 && recipe.inputs().get(3).count() == 8,
                "Construction must use four Steel Tanks, three Steel Pipes, 12 Titanium and eight Copper");
        helper.succeed();
    }

    private static FluidBurnerBlockEntity bareBurner(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.HEATER_OILBURNER.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void loadState(GameTestHelper helper, FluidBurnerBlockEntity burner,
                                  FluidIdentifierItem.Selection selection, int fuel, int heat,
                                  boolean isOn, int setting) {
        CompoundTag state = burner.saveWithoutMetadata(helper.getLevel().registryAccess());
        state.putString("selectedFluid", selection.id());
        state.putInt("tank", fuel);
        state.putInt("heatEnergy", heat);
        state.putBoolean("isOn", isOn);
        state.putByte("setting", (byte) setting);
        burner.loadWithComponents(state, helper.getLevel().registryAccess());
    }

    private static void tick(GameTestHelper helper, FluidBurnerBlockEntity burner) {
        FluidBurnerBlockEntity.tick(helper.getLevel(), burner.getBlockPos(), burner.getBlockState(), burner);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
