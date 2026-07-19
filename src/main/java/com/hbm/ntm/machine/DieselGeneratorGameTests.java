package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.DieselGeneratorBlockEntity;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.item.CastPlateItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.item.ShellItem;
import com.hbm.ntm.recipe.AssemblyRecipe;
import com.hbm.ntm.recipe.AssemblyRecipes;
import com.hbm.ntm.recipe.DieselGeneratorFuels;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DieselGeneratorGameTests {
    private DieselGeneratorGameTests() { }

    @GameTest(template = "empty")
    public static void fuelGradesAndConstructionRecipeAreExact(GameTestHelper helper) {
        check(helper, HbmConfig.DIESEL_POWER_CAPACITY.getDefault() == 50_000L
                        && HbmConfig.DIESEL_CONFIG_FUEL_CAPACITY.getDefault() == 16_000
                        && DieselGeneratorBlockEntity.FUEL_CAPACITY == 4_000,
                "The source must keep its 50,000 HE cap, dead 16,000 config value, and actual 4,000 mB tank");
        check(helper, DieselGeneratorFuels.energyPerMb(FluidIdentifierItem.Selection.NAPHTHA) == 82L
                        && DieselGeneratorFuels.energyPerMb(FluidIdentifierItem.Selection.LIGHTOIL) == 1_100L
                        && DieselGeneratorFuels.energyPerMb(FluidIdentifierItem.Selection.DIESEL) == 1_027L
                        && DieselGeneratorFuels.energyPerMb(FluidIdentifierItem.Selection.KEROSENE) == 385L
                        && DieselGeneratorFuels.energyPerMb(FluidIdentifierItem.Selection.HYDROGEN) == 7L,
                "All registered source fuels must preserve integer-first HE-per-mB conversion");
        check(helper, !DieselGeneratorFuels.accepted(FluidIdentifierItem.Selection.HEAVYOIL)
                        && !DieselGeneratorFuels.accepted(FluidIdentifierItem.Selection.HEATINGOIL)
                        && !DieselGeneratorFuels.accepted(FluidIdentifierItem.Selection.SMEAR)
                        && !DieselGeneratorFuels.accepted(FluidIdentifierItem.Selection.GAS)
                        && !DieselGeneratorFuels.accepted(FluidIdentifierItem.Selection.PETROLEUM),
                "Low-grade oils and unsupported gaseous grade fuels must remain rejected");

        AssemblyRecipe recipe = AssemblyRecipes.byName("ass.dieselgen");
        check(helper, recipe != null && recipe.duration() == 200 && recipe.power() == 100
                        && recipe.output().is(ModItems.MACHINE_DIESEL_ITEM.get()) && recipe.inputs().size() == 3,
                "ass.dieselgen must be a three-input 200-tick, 100 HE/t operation");
        check(helper, recipe.inputs().get(0).matches(ShellItem.steel(ModItems.SHELL.get(), 1))
                        && recipe.inputs().get(1).matches(CastPlateItem.create(ModItems.PLATE_CAST.get(),
                        CastPlateItem.CastPlateMaterial.COPPER, 2))
                        && recipe.inputs().get(2).matches(new ItemStack(ModItems.COIL_COPPER.get(), 4)),
                "ass.dieselgen must require one Steel Shell, two Cast Copper Plates and four Copper Coils");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void generationRedstoneWasteAndSmokeCapabilityMatchSource(GameTestHelper helper) {
        BlockPos relative = new BlockPos(3, 1, 3);
        helper.setBlock(relative, ModBlocks.MACHINE_DIESEL.get().defaultBlockState());
        DieselGeneratorBlockEntity generator = helper.getBlockEntity(relative);
        generator.setSelectedForTest(FluidIdentifierItem.Selection.DIESEL);
        check(helper, generator.addFuelForTest(3) == 3, "Diesel must enter the selected 4,000 mB fuel tank");
        generator.setPower(generator.maxPower());
        generator.generateForTest(helper.getLevel());
        check(helper, generator.fuelAmount() == 2 && generator.getPower() == generator.maxPower()
                        && generator.active(),
                "The source quirk must consume one mB and remain active even with a full HE buffer");

        helper.setBlock(relative.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        generator.generateForTest(helper.getLevel());
        check(helper, generator.fuelAmount() == 2 && !generator.active(),
                "Neighbor redstone power must stop generation without consuming fuel");

        IFluidHandler handler = generator.fluidHandler(null);
        check(helper, handler.fill(new FluidStack(ModFluids.KEROSENE.get(), 1_000),
                        IFluidHandler.FluidAction.SIMULATE) == 0
                        && handler.drain(new FluidStack(ModFluids.DIESEL.get(), 1),
                        IFluidHandler.FluidAction.SIMULATE).isEmpty(),
                "The fluid capability must accept only the selected fuel and never expose fuel for draining");
        generator.addSmokeForTest(10);
        FluidStack smoke = handler.drain(4, IFluidHandler.FluidAction.EXECUTE);
        check(helper, smoke.is(ModFluids.SMOKE.get()) && smoke.getAmount() == 4 && generator.smokeAmount() == 6,
                "The second capability tank must expose source Smoke as output-only");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void identifierFuelPowerAndSmokePersist(GameTestHelper helper) {
        BlockPos relative = new BlockPos(3, 1, 3);
        helper.setBlock(relative, ModBlocks.MACHINE_DIESEL.get().defaultBlockState());
        DieselGeneratorBlockEntity generator = helper.getBlockEntity(relative);
        generator.setSelectedForTest(FluidIdentifierItem.Selection.KEROSENE);
        generator.addFuelForTest(2_345);
        generator.addSmokeForTest(77);
        generator.setPower(12_345L);
        CompoundTag saved = generator.saveWithoutMetadata(helper.getLevel().registryAccess());

        DieselGeneratorBlockEntity restored = new DieselGeneratorBlockEntity(
                helper.absolutePos(relative.above()), ModBlocks.MACHINE_DIESEL.get().defaultBlockState());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(helper, restored.selectedFluid() == FluidIdentifierItem.Selection.KEROSENE
                        && restored.fuelAmount() == 2_345 && restored.smokeAmount() == 77
                        && restored.getPower() == 12_345L,
                "Selected fuel, tank contents, smoke buffer and HE must all round-trip through NBT");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
