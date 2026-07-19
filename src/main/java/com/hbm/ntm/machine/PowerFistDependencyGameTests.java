package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.ChemicalPlantBlockEntity;
import com.hbm.ntm.fluid.FluidTankProperties;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.SourceFluidContainerItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.item.WireFineItem;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.recipe.ChemicalPlantRecipes;
import com.hbm.ntm.recipe.ShredderRecipes;
import com.hbm.ntm.recipe.SolderingRecipes;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerFistDependencyGameTests {
    private static final ResourceLocation TANTALIUM_CAP_ALUMINIUM = id(
            "circuit_capacitor_tantalium_from_aluminium");
    private static final ResourceLocation TANTALIUM_CAP_COPPER = id(
            "circuit_capacitor_tantalium_from_copper");
    private static final ResourceLocation MULTITOOL = id("ass.multitool");

    private PowerFistDependencyGameTests() { }

    @GameTest(template = "empty")
    public static void tantaliumCapacitorUsesExactVerticalSourceRecipes(GameTestHelper helper) {
        ItemStack plate = new ItemStack(ModItems.PLATE_POLYMER.get());
        ItemStack nugget = new ItemStack(ModItems.get("nugget_tantalium").get());
        ItemStack aluminium = WireFineItem.create(
                ModItems.WIRE_FINE.get(), WireFineItem.WireMaterial.ALUMINIUM, 1);
        ItemStack copper = WireFineItem.create(
                ModItems.WIRE_FINE.get(), WireFineItem.WireMaterial.COPPER, 1);

        assertCraftsCapacitor(helper, TANTALIUM_CAP_ALUMINIUM,
                CraftingInput.of(1, 3, List.of(plate.copy(), nugget.copy(), aluminium)));
        assertCraftsCapacitor(helper, TANTALIUM_CAP_COPPER,
                CraftingInput.of(1, 3, List.of(plate.copy(), nugget.copy(), copper)));

        CraftingInput reversed = CraftingInput.of(1, 3, List.of(nugget, plate, copper));
        check(helper, helper.getLevel().getRecipeManager()
                        .getRecipeFor(RecipeType.CRAFTING, reversed, helper.getLevel()).isEmpty(),
                "The source Tantalium Capacitor recipe must remain plate/nugget/wire from top to bottom");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tantaliumFormsUseNormalNuggetIngotBlockConversions(GameTestHelper helper) {
        assertRecipeOutput(helper, "ingot_tantalium_from_nugget", "ingot_tantalium", 1);
        assertRecipeOutput(helper, "nugget_tantalium_from_ingot", "nugget_tantalium", 9);
        assertRecipeOutput(helper, "tantalium_block", "block_tantalium", 1);
        assertRecipeOutput(helper, "ingot_tantalium_from_block_tantalium", "ingot_tantalium", 9);

        TagKey<Item> nuggets = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "nuggets/tantalum"));
        check(helper, new ItemStack(ModItems.get("nugget_tantalium").get()).is(nuggets),
                "hbm:nugget_tantalium must populate c:nuggets/tantalum");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tantaliumAcquisitionChainMatchesTheSource(GameTestHelper helper) {
        ChemicalPlantRecipes.ChemicalRecipe oxygen = ChemicalPlantRecipes.get(ChemicalPlantRecipes.OXYGEN);
        check(helper, oxygen != null
                        && oxygen.duration() == 20
                        && oxygen.power() == 400L
                        && oxygen.itemInputs().isEmpty()
                        && oxygen.fluidInputs().size() == 1
                        && oxygen.fluidInputs().getFirst().fluid().get().isSame(ModFluids.AIR.get())
                        && oxygen.fluidInputs().getFirst().amount() == 8_000
                        && oxygen.itemOutputs().isEmpty()
                        && oxygen.fluidOutputs().size() == 1
                        && oxygen.fluidOutputs().getFirst().fluid().get().isSame(ModFluids.OXYGEN.get())
                        && oxygen.fluidOutputs().getFirst().amount() == 500,
                "chem.oxygen must turn 8,000 mB Air into 500 mB Liquid Oxygen in 20 ticks at 400 HE/t");

        ChemicalPlantRecipes.ChemicalRecipe solution = ChemicalPlantRecipes.get(
                ChemicalPlantRecipes.COLTAN_PAIN);
        check(helper, solution != null
                        && solution.duration() == 120
                        && solution.power() == 100L
                        && solution.itemInputs().size() == 2
                        && solution.itemInputs().get(0).matches(
                        new ItemStack(ModItems.get("powder_coltan").get()))
                        && solution.itemInputs().get(1).matches(
                        new ItemStack(ModItems.get("fluorite").get()))
                        && solution.fluidInputs().size() == 2
                        && solution.fluidInputs().get(0).fluid().get().isSame(ModFluids.GAS.get())
                        && solution.fluidInputs().get(0).amount() == 1_000
                        && solution.fluidInputs().get(1).fluid().get().isSame(ModFluids.OXYGEN.get())
                        && solution.fluidInputs().get(1).amount() == 500
                        && solution.itemOutputs().isEmpty()
                        && solution.fluidOutputs().size() == 1
                        && solution.fluidOutputs().getFirst().fluid().get().isSame(ModFluids.PAIN.get())
                        && solution.fluidOutputs().getFirst().amount() == 1_000,
                "chem.coltanpain must preserve the exact Purified Tantalite, Fluorite, Gas and Oxygen stage");

        ChemicalPlantRecipes.ChemicalRecipe crystal = ChemicalPlantRecipes.get(
                ChemicalPlantRecipes.COLTAN_CRYSTAL);
        check(helper, crystal != null
                        && crystal.duration() == 80
                        && crystal.power() == 100L
                        && crystal.itemInputs().isEmpty()
                        && crystal.fluidInputs().size() == 2
                        && crystal.fluidInputs().get(0).fluid().get().isSame(ModFluids.PAIN.get())
                        && crystal.fluidInputs().get(0).amount() == 1_000
                        && crystal.fluidInputs().get(1).fluid().get().isSame(ModFluids.PEROXIDE.get())
                        && crystal.fluidInputs().get(1).amount() == 500
                        && crystal.itemOutputs().size() == 2
                        && crystal.itemOutputs().get(0).is(ModItems.get("gem_tantalium").get())
                        && crystal.itemOutputs().get(0).getCount() == 1
                        && crystal.itemOutputs().get(1).is(ModItems.get("dust").get())
                        && crystal.itemOutputs().get(1).getCount() == 3
                        && crystal.fluidOutputs().size() == 1
                        && crystal.fluidOutputs().getFirst().fluid().get()
                        .isSame(net.minecraft.world.level.material.Fluids.WATER)
                        && crystal.fluidOutputs().getFirst().amount() == 250,
                "chem.coltancrystal must make one Tantalium Polycrystal, three Dust and 250 mB Water");

        ItemStack powder = ShredderRecipes.getResult(new ItemStack(ModItems.get("gem_tantalium").get()));
        check(helper, powder.is(ModItems.get("powder_tantalium").get()) && powder.getCount() == 1,
                "The Shredder must turn one Tantalium Polycrystal into one Tantalium Powder");

        var smelting = helper.getLevel().getRecipeManager().byKey(id("ingot_tantalium_from_powder"));
        check(helper, smelting.isPresent()
                        && smelting.get().value() instanceof AbstractCookingRecipe cooking
                        && cooking.getIngredients().size() == 1
                        && cooking.getIngredients().getFirst().test(powder)
                        && cooking.getResultItem(helper.getLevel().registryAccess())
                        .is(ModItems.get("ingot_tantalium").get())
                        && Math.abs(cooking.getExperience() - 1.0F) < 0.0001F,
                "Tantalium Powder must smelt into a Tantalium Ingot with 1.0 XP");

        TagKey<Item> gems = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "gems/tantalum"));
        TagKey<Item> dusts = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "dusts/tantalum"));
        FluidTankProperties.Profile oxygenProfile = FluidTankProperties.get(FluidIdentifierItem.Selection.OXYGEN);
        FluidTankProperties.Profile painProfile = FluidTankProperties.get(FluidIdentifierItem.Selection.PAIN);
        check(helper, new ItemStack(ModItems.get("gem_tantalium").get()).is(gems)
                        && powder.is(dusts)
                        && FluidIdentifierItem.Selection.fromFluid(ModFluids.OXYGEN.get())
                        == FluidIdentifierItem.Selection.OXYGEN
                        && FluidIdentifierItem.Selection.fromFluid(ModFluids.PAIN.get())
                        == FluidIdentifierItem.Selection.PAIN
                        && SourceFluidContainerItem.ContainedFluid.fromFluid(ModFluids.OXYGEN.get())
                        == SourceFluidContainerItem.ContainedFluid.OXYGEN
                        && SourceFluidContainerItem.ContainedFluid.OXYGEN.containerColor() == 0x98BDF9
                        && SourceFluidContainerItem.ContainedFluid.OXYGEN.labelColor() == 0xFFFFFF
                        && UniversalFluidTankItem.ContainedFluid.fromFluid(ModFluids.PAIN.get())
                        == UniversalFluidTankItem.ContainedFluid.PAIN
                        && UniversalFluidTankItem.ContainedFluid.PAIN.color() == 0x938541
                        && oxygenProfile.health() == 3
                        && oxygenProfile.flammability() == 0
                        && oxygenProfile.reactivity() == 0
                        && oxygenProfile.symbol() == FluidTankProperties.Symbol.CRYOGENIC
                        && oxygenProfile.liquid()
                        && painProfile.health() == 2
                        && painProfile.flammability() == 0
                        && painProfile.reactivity() == 1
                        && painProfile.symbol() == FluidTankProperties.Symbol.ACID
                        && painProfile.liquid(),
                "Tantalium forms, Oxygen and Pandemonium solution must expose their source tags, containers and hazards");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chemicalPlantRunsTheTantaliumStagesEndToEnd(GameTestHelper helper) {
        ChemicalPlantBlockEntity plant = barePlant(helper, new BlockPos(3, 1, 3));
        check(helper, plant.selectRecipe(ChemicalPlantRecipes.OXYGEN),
                "The source Oxygen recipe must be selectable");
        plant.setPower(16_000L);
        plant.inputTank(0).fill(new FluidStack(ModFluids.AIR.get(), 16_000),
                IFluidHandler.FluidAction.EXECUTE);
        plant.setItem(16, new ItemStack(ModItems.GAS_EMPTY.get()));
        tick(helper, plant, 41);
        ItemStack oxygenTank = plant.removeItemNoUpdate(19);
        check(helper, oxygenTank.is(ModItems.GAS_FULL.get())
                        && SourceFluidContainerItem.fluid(oxygenTank)
                        == SourceFluidContainerItem.ContainedFluid.OXYGEN
                        && plant.inputTank(0).isEmpty()
                        && plant.outputTank(0).isEmpty(),
                "Two Oxygen batches must fill one source gas tank with 1,000 mB Liquid Oxygen");

        check(helper, plant.selectRecipe(ChemicalPlantRecipes.COLTAN_PAIN),
                "The source Pandemonium solution recipe must be selectable");
        plant.setPower(12_100L);
        plant.setItem(4, new ItemStack(ModItems.get("powder_coltan").get()));
        plant.setItem(5, new ItemStack(ModItems.get("fluorite").get()));
        plant.setItem(10, SourceFluidContainerItem.create(ModItems.GAS_FULL.get(),
                SourceFluidContainerItem.ContainedFluid.GAS, 1));
        plant.setItem(11, oxygenTank);
        plant.setItem(16, new ItemStack(ModItems.FLUID_TANK_EMPTY.get()));
        tick(helper, plant, 122);
        ItemStack painTank = plant.removeItemNoUpdate(19);
        check(helper, painTank.is(ModItems.FLUID_TANK_FULL.get())
                        && UniversalFluidTankItem.fluid(painTank)
                        == UniversalFluidTankItem.ContainedFluid.PAIN
                        && plant.getItem(4).isEmpty()
                        && plant.getItem(5).isEmpty()
                        && plant.outputTank(0).isEmpty(),
                "Purified Tantalite and Fluorite must produce a full Pandemonium solution tank");

        plant.setItem(13, ItemStack.EMPTY);
        plant.setItem(14, ItemStack.EMPTY);
        check(helper, plant.selectRecipe(ChemicalPlantRecipes.COLTAN_CRYSTAL),
                "The source Tantalium crystallizing recipe must be selectable");
        plant.setPower(8_100L);
        plant.setItem(10, painTank);
        plant.setItem(11, UniversalFluidTankItem.create(ModItems.FLUID_TANK_FULL.get(),
                UniversalFluidTankItem.ContainedFluid.PEROXIDE, 1));
        tick(helper, plant, 81);
        check(helper, plant.getItem(7).is(ModItems.get("gem_tantalium").get())
                        && plant.getItem(7).getCount() == 1
                        && plant.getItem(8).is(ModItems.get("dust").get())
                        && plant.getItem(8).getCount() == 3
                        && plant.outputTank(0).getFluid().is(net.minecraft.world.level.material.Fluids.WATER)
                        && plant.outputTank(0).getFluidAmount() == 250,
                "The live Chemical Plant must finish the source chain at Tantalium Polycrystal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void capacitorBoardUsesExactPeroxideSolderingRecipe(GameTestHelper helper) {
        ItemStack[] inputs = new ItemStack[11];
        Arrays.fill(inputs, ItemStack.EMPTY);
        inputs[0] = CircuitItem.create(
                ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CAPACITOR_TANTALIUM, 3);
        inputs[3] = CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.PCB, 1);
        inputs[5] = WireFineItem.create(ModItems.WIRE_FINE.get(), WireFineItem.WireMaterial.LEAD, 3);

        SolderingRecipes.SolderingRecipe recipe = SolderingRecipes.find(inputs);
        check(helper, recipe != null
                        && CircuitItem.type(recipe.output()) == CircuitItem.CircuitType.CAPACITOR_BOARD
                        && recipe.output().getCount() == 1
                        && recipe.duration() == 200
                        && recipe.consumption() == 300L
                        && recipe.fluid() == ModFluids.PEROXIDE
                        && recipe.fluidAmount() == 250,
                "3 Tantalium Capacitors + 1 PCB + 3 Lead Wire + 250 mB Peroxide must make one board"
                        + " in 200 ticks at 300 HE/t");

        inputs[0] = CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CAPACITOR, 3);
        check(helper, SolderingRecipes.find(inputs) == null,
                "Ordinary Capacitors must not substitute for source Tantalium Capacitors");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void multitoolAssemblyRecipePreservesEverySourceLane(GameTestHelper helper) {
        AssemblyRecipe recipe = AssemblyRecipes.get(MULTITOOL);
        ItemStack namedGoldWire = WireFineItem.create(
                ModItems.WIRE_FINE.get(), WireFineItem.WireMaterial.GOLD, 12);
        namedGoldWire.set(DataComponents.CUSTOM_NAME, Component.literal("qualified gold lot"));
        ItemStack namedBoard = CircuitItem.create(
                ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CAPACITOR_BOARD, 16);
        namedBoard.set(DataComponents.CUSTOM_NAME, Component.literal("qualified capacitor lot"));

        check(helper, recipe != null
                        && recipe.inputs().size() == 5
                        && recipe.duration() == 100
                        && recipe.power() == 100L
                        && recipe.inputs().get(0).matches(new ItemStack(ModItems.get("ingot_tcalloy").get(), 4))
                        && recipe.inputs().get(0).matches(new ItemStack(ModItems.get("ingot_cdalloy").get(), 4))
                        && !recipe.inputs().get(0).matches(new ItemStack(ModItems.get("ingot_tcalloy").get(), 3))
                        && recipe.inputs().get(1).matches(new ItemStack(ModItems.get("plate_steel").get(), 4))
                        && recipe.inputs().get(2).matches(namedGoldWire)
                        && !recipe.inputs().get(2).matches(WireFineItem.create(
                        ModItems.WIRE_FINE.get(), WireFineItem.WireMaterial.COPPER, 12))
                        && recipe.inputs().get(3).matches(new ItemStack(ModItems.MOTOR.get(), 4))
                        && recipe.inputs().get(4).matches(namedBoard)
                        && !recipe.inputs().get(4).matches(CircuitItem.create(
                        ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CAPACITOR, 16))
                        && BuiltInRegistries.ITEM.getKey(recipe.output().getItem()).equals(id("multitool_hit"))
                        && recipe.output().getCount() == 1,
                "ass.multitool must preserve 4 Resistant Alloy, 4 Steel Plate, 12 Fine Gold Wire,"
                        + " 4 Motors, 16 Capacitor Boards and the original 100x100 operation");
        helper.succeed();
    }

    private static void assertCraftsCapacitor(GameTestHelper helper, ResourceLocation expected,
                                               CraftingInput input) {
        var recipe = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        ItemStack result = recipe.value().assemble(input, helper.getLevel().registryAccess());
        check(helper, recipe.id().equals(expected)
                        && result.is(ModItems.CIRCUIT.get())
                        && result.getCount() == 1
                        && CircuitItem.type(result) == CircuitItem.CircuitType.CAPACITOR_TANTALIUM,
                expected + " must produce one exact metadata-2 Tantalium Capacitor");
    }

    private static void assertRecipeOutput(GameTestHelper helper, String recipeId, String itemId, int count) {
        ItemStack result = helper.getLevel().getRecipeManager().byKey(id(recipeId)).orElseThrow().value()
                .getResultItem(helper.getLevel().registryAccess());
        check(helper, result.is(ModItems.get(itemId).get()) && result.getCount() == count,
                "hbm:" + recipeId + " must output " + count + " hbm:" + itemId);
    }

    private static ChemicalPlantBlockEntity barePlant(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_CHEMICAL_PLANT.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void tick(GameTestHelper helper, ChemicalPlantBlockEntity plant, int count) {
        for (int tick = 0; tick < count; tick++) {
            ChemicalPlantBlockEntity.tick(helper.getLevel(), plant.getBlockPos(), plant.getBlockState(), plant);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
