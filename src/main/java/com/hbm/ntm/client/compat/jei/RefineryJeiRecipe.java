package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.blockentity.RefineryBlockEntity;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Display-only wrapper for the Oil Refinery's single hardcoded Hot Oil split, read straight from
 * {@link RefineryBlockEntity}'s real constants so JEI never keeps a second copy of the numbers.
 */
public record RefineryJeiRecipe(FluidStack input, List<FluidStack> outputs,
                                ItemStack byproduct, int byproductChance) {
    public static List<RefineryJeiRecipe> all() {
        int[] amounts = RefineryBlockEntity.outputAmounts();
        List<FluidStack> outputs = List.of(
                new FluidStack(ModFluids.HEAVYOIL.get(), amounts[0]),
                new FluidStack(ModFluids.NAPHTHA.get(), amounts[1]),
                new FluidStack(ModFluids.LIGHTOIL.get(), amounts[2]),
                new FluidStack(ModFluids.PETROLEUM.get(), amounts[3]));
        return List.of(new RefineryJeiRecipe(
                new FluidStack(ModFluids.HOTOIL.get(), RefineryBlockEntity.INPUT_PER_OPERATION),
                outputs, new ItemStack(ModItems.get("sulfur").get()), 10));
    }
}
