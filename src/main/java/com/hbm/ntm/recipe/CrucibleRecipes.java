package com.hbm.ntm.recipe;

import com.hbm.ntm.block.StoneResourceBlock;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.item.StoneResourceBlockItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/** Crucible alloys. IDs are save data, so hands off. */
public final class CrucibleRecipes {
    public static final int NONE = 0;
    public static final int STEEL = 1;
    public static final int DURA_STEEL = 2;
    public static final int RED_COPPER = 3;
    public static final int FERROURANIUM = 4;
    public static final int TECHNETIUM_STEEL = 5;
    public static final int CADMIUM_STEEL = 6;
    public static final int BISMUTH_BRONZE = 7;
    public static final int ARSENIC_BRONZE = 8;
    public static final int BSCCO = 9;
    public static final int HEMATITE = 10;
    public static final int MALACHITE = 11;
    public static final int MAGNETIZED_TUNGSTEN = 12;
    public static final int COMBINE_STEEL = 13;

    private static final int N = FoundryMaterial.NUGGET;
    private static final int I = FoundryMaterial.INGOT;

    private static final List<Recipe> RECIPES = List.of(
            recipe(STEEL, "crucible.steel", 20,
                    amounts(FoundryMaterial.IRON, N * 2, FoundryMaterial.CARBON, N * 3,
                            FoundryMaterial.FLUX, N),
                    amounts(FoundryMaterial.STEEL, N * 2)),
            recipe(DURA_STEEL, "crucible.hss", 9,
                    amounts(FoundryMaterial.STEEL, N * 5, FoundryMaterial.TUNGSTEN, N * 3,
                            FoundryMaterial.COBALT, N),
                    amounts(FoundryMaterial.DURA_STEEL, I)),
            recipe(RED_COPPER, "crucible.redcopper", 2,
                    amounts(FoundryMaterial.COPPER, N, FoundryMaterial.REDSTONE, N),
                    amounts(FoundryMaterial.RED_COPPER, N * 2)),
            recipe(FERROURANIUM, "crucible.ferro", 3,
                    amounts(FoundryMaterial.STEEL, N * 2, FoundryMaterial.URANIUM_238, N),
                    amounts(FoundryMaterial.FERROURANIUM, N * 3)),
            recipe(TECHNETIUM_STEEL, "crucible.tcalloy", 9,
                    amounts(FoundryMaterial.STEEL, N * 8, FoundryMaterial.TECHNETIUM, N),
                    amounts(FoundryMaterial.TECHNETIUM_STEEL, I)),
            recipe(CADMIUM_STEEL, "crucible.cdalloy", 9,
                    amounts(FoundryMaterial.STEEL, N * 8, FoundryMaterial.CADMIUM, N),
                    amounts(FoundryMaterial.CADMIUM_STEEL, I)),
            recipe(BISMUTH_BRONZE, "crucible.bbronze", 9,
                    amounts(FoundryMaterial.COPPER, N * 8, FoundryMaterial.BISMUTH, N,
                            FoundryMaterial.FLUX, N * 3),
                    amounts(FoundryMaterial.BISMUTH_BRONZE, I, FoundryMaterial.SLAG, N * 3)),
            recipe(ARSENIC_BRONZE, "crucible.abronze", 9,
                    amounts(FoundryMaterial.COPPER, N * 8, FoundryMaterial.ARSENIC, N,
                            FoundryMaterial.FLUX, N * 3),
                    amounts(FoundryMaterial.ARSENIC_BRONZE, I, FoundryMaterial.SLAG, N * 3)),
            recipe(BSCCO, "crucible.bscco", 3,
                    amounts(FoundryMaterial.BISMUTH, N * 2, FoundryMaterial.STRONTIUM, N * 2,
                            FoundryMaterial.CALCIUM, N * 2, FoundryMaterial.COPPER, N * 3),
                    amounts(FoundryMaterial.BSCCO, I)),
            recipe(HEMATITE, "crucible.hematite", 6,
                    amounts(FoundryMaterial.HEMATITE, I * 2, FoundryMaterial.FLUX, N * 2),
                    amounts(FoundryMaterial.IRON, I, FoundryMaterial.SLAG, N * 3),
                    () -> StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(),
                            StoneResourceBlock.Type.HEMATITE, 1)),
            recipe(MALACHITE, "crucible.malachite", 6,
                    amounts(FoundryMaterial.MALACHITE, I * 2, FoundryMaterial.FLUX, N * 2),
                    amounts(FoundryMaterial.COPPER, I, FoundryMaterial.SLAG, N * 3),
                    () -> StoneResourceBlockItem.create(ModItems.STONE_RESOURCE_ITEM.get(),
                            StoneResourceBlock.Type.MALACHITE, 1)),
            recipe(MAGNETIZED_TUNGSTEN, "crucible.magtung", 3,
                    amounts(FoundryMaterial.TUNGSTEN, I, FoundryMaterial.SCHRABIDIUM, N),
                    amounts(FoundryMaterial.MAGNETIZED_TUNGSTEN, I)),
            recipe(COMBINE_STEEL, "crucible.cmb", 3,
                    amounts(FoundryMaterial.MAGNETIZED_TUNGSTEN, N * 6, FoundryMaterial.MUD, N * 3),
                    amounts(FoundryMaterial.COMBINE_STEEL, I))
    );

    /** Button order is not recipe ID order. History made sure of that. */
    private static final List<Integer> CYCLE_ORDER = List.of(
            NONE, STEEL, HEMATITE, MALACHITE, RED_COPPER, DURA_STEEL,
            FERROURANIUM, TECHNETIUM_STEEL, CADMIUM_STEEL, BISMUTH_BRONZE,
            ARSENIC_BRONZE, COMBINE_STEEL, MAGNETIZED_TUNGSTEN, BSCCO
    );

    private CrucibleRecipes() { }

    public static Recipe byId(int id) {
        return id <= NONE || id > RECIPES.size() ? null : RECIPES.get(id - 1);
    }

    public static int countWithNone() { return RECIPES.size() + 1; }
    public static int lastId() { return RECIPES.size(); }
    public static List<Recipe> all() { return RECIPES; }
    public static List<Recipe> selectorOrder() {
        return CYCLE_ORDER.stream().filter(id -> id != NONE).map(CrucibleRecipes::byId).toList();
    }

    public static int nextId(int currentId) {
        int index = CYCLE_ORDER.indexOf(currentId);
        return CYCLE_ORDER.get((index < 0 ? 0 : index + 1) % CYCLE_ORDER.size());
    }

    private static Recipe recipe(int id, String translationKey, int frequency,
                                 List<FoundryMaterial.MaterialAmount> inputs,
                                 List<FoundryMaterial.MaterialAmount> outputs) {
        return recipe(id, translationKey, frequency, inputs, outputs,
                () -> outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst().material().ingot());
    }

    private static Recipe recipe(int id, String translationKey, int frequency,
                                 List<FoundryMaterial.MaterialAmount> inputs,
                                 List<FoundryMaterial.MaterialAmount> outputs,
                                 Supplier<ItemStack> icon) {
        return new Recipe(id, translationKey, frequency, inputs, outputs, icon);
    }

    private static List<FoundryMaterial.MaterialAmount> amounts(Object... values) {
        var result = new java.util.ArrayList<FoundryMaterial.MaterialAmount>(values.length / 2);
        for (int index = 0; index < values.length; index += 2) {
            result.add(new FoundryMaterial.MaterialAmount(
                    (FoundryMaterial) values[index], (Integer) values[index + 1]));
        }
        return List.copyOf(result);
    }

    public record Recipe(int id, String translationKey, int frequency,
                         List<FoundryMaterial.MaterialAmount> inputs,
                         List<FoundryMaterial.MaterialAmount> outputs,
                         Supplier<ItemStack> iconSupplier) {
        public int inputAmount() {
            return inputs.stream().mapToInt(FoundryMaterial.MaterialAmount::amount).sum();
        }

        public int inputAmount(FoundryMaterial material) {
            return amount(inputs, material);
        }

        public boolean contains(FoundryMaterial material) {
            return inputAmount(material) > 0 || outputAmount(material) > 0;
        }

        public int outputAmount(FoundryMaterial material) {
            return amount(outputs, material);
        }

        public ItemStack icon() {
            return iconSupplier.get();
        }

        private static int amount(List<FoundryMaterial.MaterialAmount> amounts, FoundryMaterial material) {
            return amounts.stream().filter(amount -> amount.material() == material)
                    .mapToInt(FoundryMaterial.MaterialAmount::amount).sum();
        }
    }
}
