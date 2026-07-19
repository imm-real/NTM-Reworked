package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.MachinePressBlock;
import com.hbm.ntm.blockentity.AssemblyMachineBlockEntity;
import com.hbm.ntm.blockentity.MachinePressBlockEntity;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.item.WireFineItem;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.recipe.PressRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SiliconMicrochipGameTests {
    private static final ResourceLocation CHIP = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "ass.chip");

    private SiliconMicrochipGameTests() { }

    @GameTest(template = "empty")
    public static void siliconMaterialAndPrintedWaferIdentitiesRemainDistinct(GameTestHelper helper) {
        ItemStack nugget = new ItemStack(ModItems.get("nugget_silicon").get());
        ItemStack boule = new ItemStack(ModItems.get("ingot_silicon").get());
        ItemStack wafer = new ItemStack(ModItems.get("billet_silicon").get());
        ItemStack printed = CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.SILICON, 1);
        ItemStack chip = CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CHIP, 1);
        check(helper, !ItemStack.isSameItem(wafer, printed),
                "Unprinted hbm:billet_silicon and Printed Silicon Wafer circuit metadata 4 must remain distinct items");
        check(helper, CircuitItem.type(printed) == CircuitItem.CircuitType.SILICON
                        && CircuitItem.CircuitType.SILICON.legacyMetadata() == 4,
                "Printed Silicon Wafer must preserve circuit metadata 4");
        check(helper, CircuitItem.type(chip) == CircuitItem.CircuitType.CHIP
                        && CircuitItem.CircuitType.CHIP.legacyMetadata() == 5,
                "Microchip must preserve circuit metadata 5");
        check(helper, !nugget.is(boule.getItem()) && !boule.is(wafer.getItem()),
                "Silicon Nugget, Boule, and unprinted Wafer must retain separate registry identities");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allSixSiliconConversionsLoadExactOutputs(GameTestHelper helper) {
        assertRecipe(helper, "ingot_silicon_from_nuggets", "ingot_silicon", 1);
        assertRecipe(helper, "nugget_silicon_from_boule", "nugget_silicon", 9);
        assertRecipe(helper, "billet_silicon_from_nuggets", "billet_silicon", 1);
        assertRecipe(helper, "nugget_silicon_from_wafer", "nugget_silicon", 6);
        assertRecipe(helper, "ingot_silicon_from_wafers", "ingot_silicon", 2);
        assertRecipe(helper, "billet_silicon_from_boules", "billet_silicon", 3);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void circuitStampPrintsOneWaferAndTakesOneWear(GameTestHelper helper) {
        ItemStack unprinted = new ItemStack(ModItems.get("billet_silicon").get());
        ItemStack circuitStamp = new ItemStack(ModItems.STAMPS.get("stamp_iron_circuit").get());
        ItemStack direct = PressRecipes.getOutput(unprinted, circuitStamp);
        check(helper, direct.is(ModItems.CIRCUIT.get())
                        && CircuitItem.type(direct) == CircuitItem.CircuitType.SILICON,
                "Any Circuit stamp must map one unprinted wafer to one Printed Silicon Wafer");

        BlockPos position = new BlockPos(1, 1, 1);
        MachinePressBlockEntity press = placePress(helper, position);
        press.setItem(MachinePressBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        press.setItem(MachinePressBlockEntity.SLOT_STAMP, circuitStamp);
        press.setItem(MachinePressBlockEntity.SLOT_INPUT, unprinted);
        for (int tick = 0; tick < 121; tick++) {
            MachinePressBlockEntity.tick(helper.getLevel(), helper.absolutePos(position),
                    helper.getBlockState(position), press);
        }
        ItemStack output = press.getItem(MachinePressBlockEntity.SLOT_OUTPUT);
        check(helper, output.is(ModItems.CIRCUIT.get())
                        && output.getCount() == 1
                        && CircuitItem.type(output) == CircuitItem.CircuitType.SILICON,
                "Burner Press completion must output exactly one Printed Silicon Wafer");
        check(helper, press.getItem(MachinePressBlockEntity.SLOT_INPUT).isEmpty(),
                "Wafer printing must consume exactly one unprinted wafer");
        check(helper, press.getItem(MachinePressBlockEntity.SLOT_STAMP).getDamageValue() == 1,
                "Wafer printing must apply exactly one normal Press stamp wear");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pressRejectsMergingPrintedWaferIntoAnotherCircuitSubtype(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        MachinePressBlockEntity press = placePress(helper, position);
        press.setItem(MachinePressBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        press.setItem(MachinePressBlockEntity.SLOT_STAMP,
                new ItemStack(ModItems.STAMPS.get("stamp_iron_circuit").get()));
        press.setItem(MachinePressBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.get("billet_silicon").get()));
        press.setItem(MachinePressBlockEntity.SLOT_OUTPUT,
                CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CAPACITOR, 1));
        MachinePressBlockEntity.tick(helper.getLevel(), helper.absolutePos(position),
                helper.getBlockState(position), press);
        check(helper, !press.canProcess(),
                "Press must reject merging Printed Silicon Wafer into component-distinct Capacitor output");
        check(helper, CircuitItem.type(press.getItem(MachinePressBlockEntity.SLOT_OUTPUT))
                        == CircuitItem.CircuitType.CAPACITOR,
                "Rejected Press output must retain the existing circuit subtype unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chipRecipeUsesExactPrintedWaferAndGoldWireLanes(GameTestHelper helper) {
        AssemblyRecipe recipe = AssemblyRecipes.get(CHIP);
        check(helper, recipe != null && recipe.duration() == 50 && recipe.power() == 250L,
                "ass.chip must preserve 50 ticks and 250 HE/t");
        ItemStack namedWafer = CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.SILICON, 1);
        namedWafer.set(DataComponents.CUSTOM_NAME, Component.literal("QA wafer"));
        ItemStack namedGoldWire = WireFineItem.create(ModItems.WIRE_FINE.get(), WireFineItem.WireMaterial.GOLD, 1);
        namedGoldWire.set(DataComponents.CUSTOM_NAME, Component.literal("QA wire"));
        check(helper, recipe.inputs().size() == 3
                        && recipe.inputs().get(0).ingredient().test(new ItemStack(ModItems.PLATE_POLYMER.get()))
                        && recipe.inputs().get(1).ingredient().test(namedWafer)
                        && !recipe.inputs().get(1).ingredient().test(new ItemStack(ModItems.get("billet_silicon").get()))
                        && recipe.inputs().get(2).ingredient().test(namedGoldWire)
                        && !recipe.inputs().get(2).ingredient().test(WireFineItem.create(ModItems.WIRE_FINE.get(),
                        WireFineItem.WireMaterial.COPPER, 1)),
                "ass.chip lanes must accept component-augmented valid inputs while rejecting wrong wafer/wire identities");
        check(helper, recipe.output().is(ModItems.CIRCUIT.get())
                        && CircuitItem.type(recipe.output()) == CircuitItem.CircuitType.CHIP,
                "ass.chip must output exact Microchip metadata 5");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chipCompletesInFiftyTicksForTwelveThousandFiveHundredHe(GameTestHelper helper) {
        AssemblyMachineBlockEntity machine = bareMachine(helper, new BlockPos(3, 1, 3));
        loadChipInputs(machine);
        check(helper, machine.selectRecipe(CHIP, false), "Server must accept ass.chip selection");
        machine.setPower(12_500L);
        for (int tick = 0; tick < 49; tick++) tick(helper, machine);
        check(helper, machine.getItem(AssemblyMachineBlockEntity.OUTPUT).isEmpty(),
                "Microchip must not appear before processing tick 50");
        check(helper, machine.getItem(4).getCount() == 1 && machine.getItem(5).getCount() == 1
                        && machine.getItem(6).getCount() == 1,
                "ass.chip inputs must remain intact until completion");
        tick(helper, machine);
        ItemStack output = machine.getItem(AssemblyMachineBlockEntity.OUTPUT);
        check(helper, output.is(ModItems.CIRCUIT.get()) && output.getCount() == 1
                        && CircuitItem.type(output) == CircuitItem.CircuitType.CHIP,
                "Tick 50 must output one exact Microchip");
        check(helper, machine.getPower() == 0L,
                "50 ticks at 250 HE/t must consume exactly 12,500 HE");
        check(helper, machine.getItem(4).isEmpty() && machine.getItem(5).isEmpty() && machine.getItem(6).isEmpty(),
                "Microchip completion must consume all three inputs atomically");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullMicrochipOutputRejectsWithoutPartialConsumption(GameTestHelper helper) {
        AssemblyMachineBlockEntity machine = bareMachine(helper, new BlockPos(3, 1, 3));
        loadChipInputs(machine);
        machine.selectRecipe(CHIP, false);
        machine.setPower(100_000L);
        machine.setItem(AssemblyMachineBlockEntity.OUTPUT,
                CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CHIP, 64));
        tick(helper, machine);
        check(helper, machine.progress() == 0D && machine.getPower() == 100_000L,
                "Full Microchip output must reject work before HE is spent");
        check(helper, machine.getItem(4).getCount() == 1 && machine.getItem(5).getCount() == 1
                        && machine.getItem(6).getCount() == 1,
                "Rejected Microchip work must not partially consume inputs");
        helper.succeed();
    }

    private static void assertRecipe(GameTestHelper helper, String id, String outputId, int count) {
        ItemStack result = helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, id)).orElseThrow().value().getResultItem(helper.getLevel().registryAccess());
        check(helper, result.is(ModItems.get(outputId).get()) && result.getCount() == count,
                "Recipe hbm:" + id + " must output " + count + " hbm:" + outputId);
    }

    private static MachinePressBlockEntity placePress(GameTestHelper helper, BlockPos position) {
        MachinePressBlock block = ModBlocks.MACHINE_PRESS.get();
        helper.setBlock(position, block.defaultBlockState().setValue(MachinePressBlock.PART,
                MachinePressBlock.PressPart.LOWER));
        helper.setBlock(position.above(), block.defaultBlockState().setValue(MachinePressBlock.PART,
                MachinePressBlock.PressPart.MIDDLE));
        helper.setBlock(position.above(2), block.defaultBlockState().setValue(MachinePressBlock.PART,
                MachinePressBlock.PressPart.UPPER));
        return helper.getBlockEntity(position);
    }

    private static AssemblyMachineBlockEntity bareMachine(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_ASSEMBLY_MACHINE.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void loadChipInputs(AssemblyMachineBlockEntity machine) {
        machine.setItem(4, new ItemStack(ModItems.PLATE_POLYMER.get()));
        machine.setItem(5, CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.SILICON, 1));
        machine.setItem(6, WireFineItem.create(ModItems.WIRE_FINE.get(), WireFineItem.WireMaterial.GOLD, 1));
    }

    private static void tick(GameTestHelper helper, AssemblyMachineBlockEntity machine) {
        AssemblyMachineBlockEntity.tick(helper.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
