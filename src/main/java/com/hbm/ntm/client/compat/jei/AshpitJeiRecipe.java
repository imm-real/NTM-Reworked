package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.AshItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public record AshpitJeiRecipe(ResourceLocation id, Ingredient source, Ingredient fuel,
                              ItemStack output) {
    public static List<AshpitJeiRecipe> all() {
        Ingredient ovens = Ingredient.of(ModItems.HEATER_FIREBOX_ITEM.get(),
                ModItems.HEATER_OVEN_ITEM.get());
        return List.of(
                recipe("coal", ovens, Ingredient.of(
                                new ItemStack(Items.COAL),
                                new ItemStack(ModItems.legacyOreResourceItem("lignite").get()),
                                new ItemStack(ModItems.COKE_COAL.get())),
                        AshItem.AshType.COAL),
                recipe("wood", ovens, Ingredient.of(
                                new ItemStack(Items.OAK_LOG),
                                new ItemStack(Items.ACACIA_LOG),
                                new ItemStack(Items.OAK_PLANKS),
                                new ItemStack(Items.OAK_SAPLING)),
                        AshItem.AshType.WOOD),
                recipe("misc", ovens, Ingredient.of(
                                new ItemStack(ModItems.SOLID_FUEL.get()),
                                new ItemStack(ModItems.get("scrap").get()),
                                new ItemStack(ModItems.get("dust").get()),
                                new ItemStack(ModItems.ROCKET_FUEL.get())),
                        AshItem.AshType.MISC));
    }

    private static AshpitJeiRecipe recipe(String path, Ingredient source, Ingredient fuel,
                                           AshItem.AshType output) {
        return new AshpitJeiRecipe(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "ashpit/" + path), source, fuel,
                AshItem.create(ModItems.POWDER_ASH.get(), output));
    }
}
