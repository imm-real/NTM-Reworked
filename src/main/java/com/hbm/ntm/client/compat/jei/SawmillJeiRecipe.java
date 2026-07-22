package com.hbm.ntm.client.compat.jei;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public record SawmillJeiRecipe(ResourceLocation id, Ingredient input, ItemStack output,
                               int sawdustChance) {
    private static final TagKey<Item> WOODEN_RODS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "rods/wooden"));

    public static List<SawmillJeiRecipe> all() {
        return List.of(
                recipe("logs", Ingredient.of(ItemTags.LOGS), new ItemStack(Items.OAK_PLANKS, 6), 50),
                recipe("planks", Ingredient.of(ItemTags.PLANKS), new ItemStack(Items.STICK, 6), 10),
                recipe("wooden_rods", Ingredient.of(WOODEN_RODS),
                        new ItemStack(ModItems.POWDER_SAWDUST.get()), 0),
                recipe("saplings", Ingredient.of(ItemTags.SAPLINGS), new ItemStack(Items.STICK), 10));
    }

    private static SawmillJeiRecipe recipe(String path, Ingredient input, ItemStack output,
                                            int sawdustChance) {
        return new SawmillJeiRecipe(ResourceLocation.fromNamespaceAndPath(
                HbmNtm.MOD_ID, "sawmill/" + path), input, output, sawdustChance);
    }
}
