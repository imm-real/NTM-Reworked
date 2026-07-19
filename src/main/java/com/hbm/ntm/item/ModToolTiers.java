package com.hbm.ntm.item;

import com.hbm.ntm.registry.ModItems;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

/** Tool materials introduced to the data-component era. */
public final class ModToolTiers {
    public static final Tier STEEL = new SimpleTier(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 750, 8.0F, 2.0F, 10,
            () -> Ingredient.of(ModItems.get("ingot_steel").get()));
    public static final Tier TITANIUM = new SimpleTier(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1_000, 9.0F, 2.5F, 15,
            () -> Ingredient.of(ModItems.get("ingot_titanium").get()));
    public static final Tier COBALT = new SimpleTier(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 750, 9.0F, 2.5F, 60,
            () -> Ingredient.EMPTY);
    public static final Tier BISMUTH = new SimpleTier(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 0, 50.0F, 0.0F, 200,
            () -> Ingredient.of(ModItems.get("ingot_bismuth").get()));

    private ModToolTiers() {
    }
}
