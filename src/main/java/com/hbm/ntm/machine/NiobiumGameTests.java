package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.ChemicalPlantBlockEntity;
import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.item.SourceFluidContainerItem;
import com.hbm.ntm.recipe.ChemicalPlantRecipes;
import com.hbm.ntm.recipe.ShredderRecipes;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import com.hbm.ntm.worldgen.ColtanDepositFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NiobiumGameTests {
    private NiobiumGameTests() { }

    @GameTest(template = "empty")
    public static void coltanDepositIdentityDropsAndSeedAnchorMatchSource(GameTestHelper helper) {
        BlockPos center = ColtanDepositFeature.depositCenter(0L);
        check(helper, center.getX() == 352 && center.getZ() == -629,
                "World seed zero must preserve the source Random(seed + 5) Coltan center");
        check(helper, ColtanDepositFeature.insideBand(center, center.getX() + 750, center.getZ(), 1)
                        && !ColtanDepositFeature.insideBand(center, center.getX() + 751, center.getZ(), 1)
                        && ColtanDepositFeature.insideBand(center, center.getX() + 150, center.getZ(), 5),
                "Coltan density bands must preserve the source 750 / band square ranges");

        BlockPos relative = new BlockPos(2, 1, 2);
        helper.setBlock(relative, ModBlocks.ORE_COLTAN.get());
        BlockPos absolute = helper.absolutePos(relative);
        var state = helper.getLevel().getBlockState(absolute);
        var drops = Block.getDrops(state, helper.getLevel(), absolute, null);
        check(helper, state.getDestroySpeed(helper.getLevel(), absolute) == 15.0F
                        && ModBlocks.ORE_COLTAN.get().getExplosionResistance() == 6.0F
                        && drops.size() == 1 && drops.getFirst().is(ModItems.get("fragment_coltan").get())
                        && drops.getFirst().getCount() == 1,
                "Coltan Ore must preserve hardness 15, effective resistance 6 and one Coltan fragment drop");

        ItemStack silkPick = new ItemStack(Items.DIAMOND_PICKAXE);
        silkPick.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH), 1);
        var silkDrops = Block.getDrops(state, helper.getLevel(), absolute, null, null, silkPick);
        ItemStack shreddedOre = ShredderRecipes.getResult(new ItemStack(ModItems.ORE_COLTAN_ITEM.get()));
        check(helper, silkDrops.size() == 1 && silkDrops.getFirst().is(ModItems.ORE_COLTAN_ITEM.get())
                        && shreddedOre.is(ModItems.get("powder_coltan_ore").get())
                        && shreddedOre.getCount() == 2,
                "Silk Touch must preserve Coltan Ore and the source Shredder must yield two Crushed Coltan");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chemicalPlantMakesHydrogenInSharedGasTank(GameTestHelper helper) {
        ChemicalPlantBlockEntity plant = barePlant(helper, new BlockPos(3, 1, 3));
        check(helper, plant.selectRecipe(ChemicalPlantRecipes.HYDROGEN),
                "The exact source Hydrogen recipe must be selectable");
        plant.setPower(16_000L);
        plant.setItem(4, new ItemStack(Items.COAL, 2));
        plant.inputTank(0).fill(new FluidStack(Fluids.WATER, 16_000), IFluidHandler.FluidAction.EXECUTE);
        plant.setItem(16, new ItemStack(ModItems.GAS_EMPTY.get()));
        for (int tick = 0; tick < 41; tick++) tick(helper, plant);

        ItemStack full = plant.getItem(19);
        IFluidHandlerItem handler = full.getCapability(Capabilities.FluidHandler.ITEM);
        check(helper, plant.getPower() == 0L && plant.getItem(4).isEmpty()
                        && plant.inputTank(0).isEmpty() && plant.outputTank(0).isEmpty()
                        && full.is(ModItems.GAS_FULL.get())
                        && SourceFluidContainerItem.fluid(full)
                        == SourceFluidContainerItem.ContainedFluid.HYDROGEN
                        && handler != null && handler.getFluidInTank(0).getAmount() == 1_000
                        && handler.getFluidInTank(0).is(ModFluids.HYDROGEN.get())
                        && plant.canPlaceItem(10, full)
                        && plant.canPlaceItem(16, new ItemStack(ModItems.GAS_EMPTY.get())),
                "Two Coal plus 16,000 mB Water must make one 1,000 mB Liquid Hydrogen gas tank "
                        + "in 40 ticks for exactly 16,000 HE, with both source gas-container slots usable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void coltanCleaningProducesSmeltableNiobiumPowder(GameTestHelper helper) {
        ItemStack crushed = ShredderRecipes.getResult(new ItemStack(ModItems.get("fragment_coltan").get()));
        check(helper, crushed.is(ModItems.get("powder_coltan_ore").get()) && crushed.getCount() == 1,
                "One Coltan fragment must shred into one Crushed Coltan");
        check(helper, crushed.getItem() instanceof HazardCarrier carrier
                        && carrier.hbm$getHazards(crushed).asbestos() == 3.0F,
                "Crushed Coltan must retain the source asbestos hazard level of 3");

        ChemicalPlantBlockEntity plant = barePlant(helper, new BlockPos(3, 1, 3));
        check(helper, plant.selectRecipe(ChemicalPlantRecipes.COLTAN_CLEANING),
                "The exact source Coltan Cleaning recipe must be selectable");
        plant.setPower(6_000L);
        plant.setItem(4, crushed.copyWithCount(2));
        plant.setItem(5, new ItemStack(ModItems.get("powder_coal").get()));
        plant.inputTank(0).fill(new FluidStack(ModFluids.PEROXIDE.get(), 250),
                IFluidHandler.FluidAction.EXECUTE);
        plant.inputTank(1).fill(new FluidStack(ModFluids.HYDROGEN.get(), 500),
                IFluidHandler.FluidAction.EXECUTE);
        for (int tick = 0; tick < 60; tick++) tick(helper, plant);
        check(helper, plant.getPower() == 0L
                        && plant.getItem(7).is(ModItems.get("powder_coltan").get())
                        && plant.getItem(8).is(ModItems.get("powder_niobium").get())
                        && plant.getItem(9).is(ModItems.get("dust").get())
                        && plant.outputTank(0).getFluidAmount() == 500
                        && plant.outputTank(0).getFluid().is(Fluids.WATER),
                "Two Crushed Coltan, Coal Powder, 250 mB Peroxide and 500 mB Hydrogen must produce "
                        + "Purified Tantalite, Niobium Powder, Dust and 500 mB Water in 60 ticks for 6,000 HE");

        ItemStack smelted = helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                        HbmNtm.MOD_ID, "ingot_niobium_from_powder"))
                .map(holder -> holder.value().getResultItem(helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        check(helper, smelted.is(ModItems.get("ingot_niobium").get()) && smelted.getCount() == 1,
                "One Niobium Powder must smelt into one Niobium Ingot with the source recipe identity");
        helper.succeed();
    }

    private static ChemicalPlantBlockEntity barePlant(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MACHINE_CHEMICAL_PLANT.get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void tick(GameTestHelper helper, ChemicalPlantBlockEntity plant) {
        ChemicalPlantBlockEntity.tick(helper.getLevel(), plant.getBlockPos(), plant.getBlockState(), plant);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
