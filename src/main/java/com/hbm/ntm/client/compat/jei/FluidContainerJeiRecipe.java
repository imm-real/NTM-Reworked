package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.SourceFluidContainerItem;
import com.hbm.ntm.item.UniversalFluidTankItem;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public record FluidContainerJeiRecipe(ResourceLocation id, FluidStack fluid,
                                      ItemStack empty, ItemStack full) {
    private static final int CAPACITY = 1_000;

    public FluidContainerJeiRecipe {
        fluid = fluid.copy();
        empty = empty.copy();
        full = full.copy();
    }

    @Override public FluidStack fluid() { return fluid.copy(); }
    @Override public ItemStack empty() { return empty.copy(); }
    @Override public ItemStack full() { return full.copy(); }

    public static List<FluidContainerJeiRecipe> all() {
        List<FluidContainerJeiRecipe> recipes = new ArrayList<>();

        for (UniversalFluidTankItem.ContainedFluid fluid
                : UniversalFluidTankItem.ContainedFluid.values()) {
            if (fluid == UniversalFluidTankItem.ContainedFluid.NONE) continue;
            recipes.add(recipe("tank/" + fluid.id(), fluid.fluid(),
                    new ItemStack(ModItems.FLUID_TANK_EMPTY.get()),
                    UniversalFluidTankItem.create(ModItems.FLUID_TANK_FULL.get(), fluid, 1)));
        }

        addSourceContainers(recipes, "canister", ModItems.CANISTER_EMPTY.get(),
                ModItems.CANISTER_FULL.get(), new SourceFluidContainerItem.ContainedFluid[]{
                        SourceFluidContainerItem.ContainedFluid.OIL,
                        SourceFluidContainerItem.ContainedFluid.HEAVYOIL,
                        SourceFluidContainerItem.ContainedFluid.NAPHTHA,
                        SourceFluidContainerItem.ContainedFluid.LIGHTOIL,
                        SourceFluidContainerItem.ContainedFluid.BITUMEN,
                        SourceFluidContainerItem.ContainedFluid.SMEAR,
                        SourceFluidContainerItem.ContainedFluid.HEATINGOIL,
                        SourceFluidContainerItem.ContainedFluid.WOODOIL,
                        SourceFluidContainerItem.ContainedFluid.COALCREOSOTE,
                        SourceFluidContainerItem.ContainedFluid.LUBRICANT,
                        SourceFluidContainerItem.ContainedFluid.DIESEL,
                        SourceFluidContainerItem.ContainedFluid.KEROSENE
                });
        addSourceContainers(recipes, "gas_tank", ModItems.GAS_EMPTY.get(),
                ModItems.GAS_FULL.get(), new SourceFluidContainerItem.ContainedFluid[]{
                        SourceFluidContainerItem.ContainedFluid.GAS,
                        SourceFluidContainerItem.ContainedFluid.PETROLEUM,
                        SourceFluidContainerItem.ContainedFluid.HYDROGEN,
                        SourceFluidContainerItem.ContainedFluid.OXYGEN,
                        SourceFluidContainerItem.ContainedFluid.DEUTERIUM,
                        SourceFluidContainerItem.ContainedFluid.TRITIUM,
                        SourceFluidContainerItem.ContainedFluid.UNSATURATEDS
                });

        recipes.add(recipe("cell/tritium", ModFluids.TRITIUM.get(),
                new ItemStack(ModItems.CELL_EMPTY.get()),
                new ItemStack(ModItems.CELL_TRITIUM.get())));
        recipes.add(recipe("cell/sas3", ModFluids.SAS3.get(),
                new ItemStack(ModItems.CELL_EMPTY.get()),
                new ItemStack(ModItems.CELL_SAS3.get())));
        return List.copyOf(recipes);
    }

    private static void addSourceContainers(List<FluidContainerJeiRecipe> recipes, String family,
                                            Item empty, Item full,
                                            SourceFluidContainerItem.ContainedFluid[] fluids) {
        for (SourceFluidContainerItem.ContainedFluid fluid : fluids) {
            recipes.add(recipe(family + "/" + fluid.id(), fluid.fluid(),
                    new ItemStack(empty), SourceFluidContainerItem.create(full, fluid, 1)));
        }
    }

    private static FluidContainerJeiRecipe recipe(String path, Fluid fluid,
                                                   ItemStack empty, ItemStack full) {
        return new FluidContainerJeiRecipe(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "fluid_container/" + path), new FluidStack(fluid, CAPACITY),
                empty, full);
    }
}
