package com.hbm.ntm.recipe;

import com.hbm.ntm.block.StoneResourceBlock;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.item.FoundryIngotItem;
import com.hbm.ntm.item.StoneResourceBlockItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Blast furnace recipe book, kept in its customary order. */
public final class BlastFurnaceRecipes {
    private static final List<BlastFurnaceRecipe> RECIPES = List.of(
            recipe("blast.steelFromIngot", 800,
                    tag("c:ingots/iron", 2, () -> new ItemStack(Items.IRON_INGOT)),
                    tag("minecraft:sand", 1, () -> new ItemStack(Items.SAND)),
                    itemOutput("ingot_steel", 2), BlastFurnaceRecipes::slag),
            recipe("blast.steelFromDust", 800,
                    tag("c:dusts/iron", 2, itemDisplay("powder_iron")),
                    tag("minecraft:sand", 1, () -> new ItemStack(Items.SAND)),
                    itemOutput("ingot_steel", 2), BlastFurnaceRecipes::slag),
            recipe("blast.steelFromOre", 800,
                    ironOre(1), tag("minecraft:sand", 1, () -> new ItemStack(Items.SAND)),
                    itemOutput("ingot_steel", 2), () -> slag(2)),
            recipe("blast.steelWithFlux", 1_200,
                    ironOre(1), item(ModItems.POWDER_FLUX, 1),
                    itemOutput("ingot_steel", 3), () -> slag(2)),

            recipe("blast.mingrade", 400,
                    tag("c:ingots/copper", 1, itemDisplay("ingot_copper")),
                    item(Items.REDSTONE, 1), itemOutput("ingot_red_copper", 2), null),
            recipe("blast.mingradeDust", 400,
                    tag("c:dusts/copper", 1, itemDisplay("powder_copper")),
                    item(Items.REDSTONE, 1), itemOutput("ingot_red_copper", 2), null),
            recipe("blast.mingradeOre", 1_200,
                    copperOre(1), item(Items.REDSTONE, 6),
                    itemOutput("ingot_red_copper", 6), BlastFurnaceRecipes::slag),

            recipe("blast.firebrick", 800,
                    tag("c:dusts/aluminum", 1, itemDisplay("powder_aluminium")),
                    item(Items.CLAY_BALL, 7), itemOutput("ingot_firebrick", 8), null),
            recipe("blast.firebrickLimestone", 800,
                    stoneResource(StoneResourceBlock.Type.LIMESTONE, 1), item(Items.CLAY_BALL, 6),
                    itemOutput("ingot_firebrick", 8), null)
    );

    private BlastFurnaceRecipes() { }

    public static List<BlastFurnaceRecipe> all() { return RECIPES; }

    public static BlastFurnaceRecipe find(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) return null;
        for (BlastFurnaceRecipe recipe : RECIPES) {
            if (recipe.first().matchesType(first) && recipe.second().matchesType(second)
                    || recipe.first().matchesType(second) && recipe.second().matchesType(first)) return recipe;
        }
        return null;
    }

    public static boolean isValidInput(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (BlastFurnaceRecipe recipe : RECIPES) {
            if (recipe.first().matchesType(stack) || recipe.second().matchesType(stack)) return true;
        }
        return false;
    }

    public static boolean inputsMatch(BlastFurnaceRecipe recipe, ItemStack first, ItemStack second) {
        return recipe.first().matches(first) && recipe.second().matches(second)
                || recipe.first().matches(second) && recipe.second().matches(first);
    }

    private static BlastFurnaceRecipe recipe(String name, int duration, Input first, Input second,
                                              Supplier<ItemStack> primary,
                                              Supplier<ItemStack> secondary) {
        return new BlastFurnaceRecipe(name, duration, first, second, primary, secondary);
    }

    private static Input item(Item item, int count) {
        return new Input(Ingredient.of(item), count, stack -> true, () -> new ItemStack(item));
    }

    private static Input item(Supplier<? extends Item> item, int count) {
        return new Input(Ingredient.of(item.get()), count, stack -> true, () -> new ItemStack(item.get()));
    }

    private static Input tag(String id, int count, Supplier<ItemStack> display) {
        return new Input(Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse(id))),
                count, stack -> true, display);
    }

    private static Input ironOre(int count) {
        Ingredient vanillaAndCompat = Ingredient.of(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "ores/iron")));
        ItemStack hematite = StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(),
                StoneResourceBlock.Type.HEMATITE, 1);
        return new Input(CompoundIngredient.of(vanillaAndCompat, DataComponentIngredient.of(false, hematite)),
                count, stack -> true, () -> hematite.copy());
    }

    private static Input copperOre(int count) {
        Ingredient vanillaAndCompat = Ingredient.of(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "ores/copper")));
        ItemStack malachite = StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(),
                StoneResourceBlock.Type.MALACHITE, 1);
        return new Input(CompoundIngredient.of(vanillaAndCompat, DataComponentIngredient.of(false, malachite)),
                count, stack -> true, () -> malachite.copy());
    }

    private static Input stoneResource(StoneResourceBlock.Type type, int count) {
        ItemStack expected = StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(), type, 1);
        return new Input(Ingredient.of(ModItems.STONE_RESOURCE_ITEM.get()), count,
                stack -> StoneResourceBlockItem.type(stack) == type, () -> expected.copy());
    }

    private static Supplier<ItemStack> itemOutput(String id, int count) {
        return () -> new ItemStack(ModItems.get(id).get(), count);
    }

    private static Supplier<ItemStack> itemDisplay(String id) {
        return () -> new ItemStack(ModItems.get(id).get());
    }

    private static ItemStack slag() { return slag(1); }
    private static ItemStack slag(int count) {
        return FoundryIngotItem.create(ModItems.INGOT_RAW.get(), FoundryMaterial.SLAG, count);
    }

    public record Input(Ingredient ingredient, int count, Predicate<ItemStack> extra,
                        Supplier<ItemStack> display) {
        public boolean matchesType(ItemStack stack) { return ingredient.test(stack) && extra.test(stack); }
        public boolean matches(ItemStack stack) { return matchesType(stack) && stack.getCount() >= count; }
    }

    public record BlastFurnaceRecipe(String name, int duration, Input first, Input second,
                                     Supplier<ItemStack> primaryOutput,
                                     Supplier<ItemStack> secondaryOutput) {
        public ItemStack primary() { return primaryOutput.get().copy(); }
        public ItemStack secondary() {
            return secondaryOutput == null ? ItemStack.EMPTY : secondaryOutput.get().copy();
        }
    }
}
