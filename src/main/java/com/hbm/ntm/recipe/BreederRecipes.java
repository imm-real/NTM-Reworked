package com.hbm.ntm.recipe;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.BreedingRodItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Exact built-in hbmBreeder defaults; an external reload layer remains a shared-system boundary. */
public final class BreederRecipes {
    public static final ResourceLocation ETCHED_SWORD = id("meteorite_sword_etched");
    public static final ResourceLocation BRED_SWORD = id("meteorite_sword_bred");
    private static final Map<BreedingRodItem.Type, BaseRecipe> RODS = defaults();

    private BreederRecipes() { }

    @Nullable public static Recipe get(ItemStack input) {
        if (input.getItem() instanceof BreedingRodItem rod) {
            BaseRecipe base = RODS.get(BreedingRodItem.type(input));
            if (base == null) return null;
            ItemStack output = BreedingRodItem.stack(input.getItem(), base.output(), 1);
            return new Recipe(output, base.flux() * rod.form().breedingFluxMultiplier());
        }
        if (BuiltInRegistries.ITEM.getKey(input.getItem()).equals(ETCHED_SWORD)) {
            Item output = BuiltInRegistries.ITEM.getOptional(BRED_SWORD).orElse(null);
            return output == null ? null : new Recipe(new ItemStack(output), 1_000);
        }
        return null;
    }

    @Nullable public static BaseRecipe rod(BreedingRodItem.Type input) { return RODS.get(input); }
    public static int rodRecipeCount() { return RODS.size() * BreedingRodItem.Form.values().length; }
    public static float progressPerTick(int flux, int requiredFlux) {
        // TileEntityMachineReactorBreeding used integer division here. Keep it: excess
        // flux advances the breeder in whole multiples of the base rate.
        return requiredFlux <= 0 ? 0.0F : 0.0025F * (flux / requiredFlux);
    }

    private static Map<BreedingRodItem.Type, BaseRecipe> defaults() {
        EnumMap<BreedingRodItem.Type, BaseRecipe> recipes = new EnumMap<>(BreedingRodItem.Type.class);
        put(recipes, BreedingRodItem.Type.LITHIUM, BreedingRodItem.Type.TRITIUM, 200);
        put(recipes, BreedingRodItem.Type.CO, BreedingRodItem.Type.CO60, 100);
        put(recipes, BreedingRodItem.Type.RA226, BreedingRodItem.Type.AC227, 300);
        put(recipes, BreedingRodItem.Type.TH232, BreedingRodItem.Type.THF, 500);
        put(recipes, BreedingRodItem.Type.U235, BreedingRodItem.Type.NP237, 300);
        put(recipes, BreedingRodItem.Type.NP237, BreedingRodItem.Type.PU238, 200);
        put(recipes, BreedingRodItem.Type.PU238, BreedingRodItem.Type.PU239, 1_000);
        put(recipes, BreedingRodItem.Type.U238, BreedingRodItem.Type.RGP, 300);
        put(recipes, BreedingRodItem.Type.URANIUM, BreedingRodItem.Type.RGP, 200);
        put(recipes, BreedingRodItem.Type.RGP, BreedingRodItem.Type.WASTE, 200);
        return Map.copyOf(recipes);
    }

    private static void put(Map<BreedingRodItem.Type, BaseRecipe> recipes,
                            BreedingRodItem.Type input, BreedingRodItem.Type output, int flux) {
        recipes.put(input, new BaseRecipe(output, flux));
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path); }

    public record BaseRecipe(BreedingRodItem.Type output, int flux) { }
    public record Recipe(ItemStack output, int flux) { }
}
