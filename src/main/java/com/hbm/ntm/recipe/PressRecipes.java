package com.hbm.ntm.recipe;

import com.hbm.ntm.item.StampItem;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.item.WireFineItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Supplier;

/**
 * Burner Press recipes. Lookup ignores stack size because the press counts later.
 */
public final class PressRecipes {
    private static final List<PressRecipe> RECIPES = List.of(
            recipe(StampItem.StampType.FLAT, Ingredient.of(Items.JUNGLE_LOG), item("ball_resin")),
            recipe(StampItem.StampType.FLAT, tag("c", "dusts/quartz"), () -> new ItemStack(Items.QUARTZ)),
            recipe(StampItem.StampType.FLAT, tag("c", "dusts/lapis"), () -> new ItemStack(Items.LAPIS_LAZULI)),
            recipe(StampItem.StampType.FLAT, tag("c", "dusts/diamond"), () -> new ItemStack(Items.DIAMOND)),
            recipe(StampItem.StampType.FLAT, tag("c", "dusts/emerald"), () -> new ItemStack(Items.EMERALD)),
            recipe(StampItem.StampType.FLAT, tag("c", "gems/coke"), item("ingot_graphite")),

            recipe(StampItem.StampType.PLATE, tag("c", "ingots/iron"), item("plate_iron")),
            recipe(StampItem.StampType.PLATE, tag("c", "ingots/gold"), item("plate_gold")),
            recipe(StampItem.StampType.PLATE, tag("c", "ingots/titanium"), item("plate_titanium")),
            recipe(StampItem.StampType.PLATE, tag("c", "ingots/aluminum"), item("plate_aluminium")),
            recipe(StampItem.StampType.PLATE, tag("c", "ingots/steel"), item("plate_steel")),
            recipe(StampItem.StampType.PLATE, tag("c", "ingots/lead"), item("plate_lead")),
            recipe(StampItem.StampType.PLATE, tag("c", "ingots/copper"), item("plate_copper")),
            recipe(StampItem.StampType.PLATE, tag("c", "ingots/gunmetal"), item("plate_gunmetal")),
            recipe(StampItem.StampType.PLATE, tag("c", "ingots/weapon_steel"), item("plate_weaponsteel")),
            recipe(StampItem.StampType.PLATE, tag("c", "ingots/dura_steel"), item("plate_dura_steel")),

            wire("graphite", WireFineItem.WireMaterial.CARBON),
            wire("gold", WireFineItem.WireMaterial.GOLD),
            wire("copper", WireFineItem.WireMaterial.COPPER),
            wire("tungsten", WireFineItem.WireMaterial.TUNGSTEN),
            wire("aluminum", WireFineItem.WireMaterial.ALUMINIUM),
            wire("lead", WireFineItem.WireMaterial.LEAD),
            wire("zirconium", WireFineItem.WireMaterial.ZIRCONIUM),
            wire("steel", WireFineItem.WireMaterial.STEEL),
            wire("red_copper", WireFineItem.WireMaterial.RED_COPPER),

            recipe(StampItem.StampType.CIRCUIT, tag("c", "billets/silicon"),
                    () -> CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.SILICON, 1))
    );

    private PressRecipes() {
    }

    public static ItemStack getOutput(ItemStack input, ItemStack stamp) {
        if (input.isEmpty() || stamp.isEmpty() || !(stamp.getItem() instanceof StampItem stampItem)) {
            return ItemStack.EMPTY;
        }

        for (PressRecipe recipe : RECIPES) {
            if (recipe.stampType() == stampItem.stampType() && recipe.input().test(input)) {
                return recipe.output().get().copy();
            }
        }
        return ItemStack.EMPTY;
    }

    public static int recipeCount() {
        return RECIPES.size();
    }

    private static PressRecipe recipe(StampItem.StampType type, Ingredient input, Supplier<ItemStack> output) {
        return new PressRecipe(type, input, output);
    }

    private static PressRecipe wire(String ingotMaterial, WireFineItem.WireMaterial material) {
        return recipe(StampItem.StampType.WIRE, tag("c", "ingots/" + ingotMaterial),
                () -> WireFineItem.create(ModItems.WIRE_FINE.get(), material, 8));
    }

    private static Ingredient tag(String namespace, String path) {
        return Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path)));
    }

    private static Supplier<ItemStack> item(String id) {
        return () -> new ItemStack(ModItems.get(id).get());
    }

    public record PressRecipe(StampItem.StampType stampType, Ingredient input, Supplier<ItemStack> output) {
    }
}
