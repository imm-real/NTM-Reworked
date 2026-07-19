package com.hbm.ntm.recipe;

import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.item.WireFineItem;
import com.hbm.ntm.registry.ModFluids;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Soldering recipes whose ingredients and outputs are registered. */
public final class SolderingRecipes {
    private static final List<SolderingRecipe> RECIPES = List.of(
            new SolderingRecipe(
                    CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.ANALOG, 1),
                    100, 100,
                    List.of(circuit(CircuitItem.CircuitType.VACUUM_TUBE, 3),
                            circuit(CircuitItem.CircuitType.CAPACITOR, 2)),
                    List.of(circuit(CircuitItem.CircuitType.PCB, 4)),
                    List.of(wire(WireFineItem.WireMaterial.LEAD, 4))
            ),
            new SolderingRecipe(
                    CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.BASIC, 1),
                    200, 250,
                    List.of(circuit(CircuitItem.CircuitType.CHIP, 4)),
                    List.of(circuit(CircuitItem.CircuitType.PCB, 4)),
                    List.of(wire(WireFineItem.WireMaterial.LEAD, 4))
            ),
            new SolderingRecipe(
                    CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.ADVANCED, 1),
                    300, 1_000, ModFluids.SULFURIC_ACID, 1_000,
                    List.of(circuit(CircuitItem.CircuitType.CHIP, 16),
                            circuit(CircuitItem.CircuitType.CAPACITOR, 4)),
                    List.of(circuit(CircuitItem.CircuitType.PCB, 8),
                            tag("c", "ingots/rubber", 2)),
                    List.of(wire(WireFineItem.WireMaterial.LEAD, 8))
            ),
            new SolderingRecipe(
                    CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CAPACITOR_BOARD, 1),
                    200, 300, ModFluids.PEROXIDE, 250,
                    List.of(circuit(CircuitItem.CircuitType.CAPACITOR_TANTALIUM, 3)),
                    List.of(circuit(CircuitItem.CircuitType.PCB, 1)),
                    List.of(wire(WireFineItem.WireMaterial.LEAD, 3))
            )
    );

    private SolderingRecipes() { }

    public static List<SolderingRecipe> all() { return RECIPES; }

    public static SolderingRecipe find(ItemStack[] inputs) {
        for (SolderingRecipe recipe : RECIPES) {
            if (matchesGroup(inputs, 0, 3, recipe.toppings())
                    && matchesGroup(inputs, 3, 5, recipe.pcb())
                    && matchesGroup(inputs, 5, 6, recipe.solder())) return recipe;
        }
        return null;
    }

    public static boolean validForGroup(int slot, ItemStack stack) {
        if (slot < 0 || slot > 5) return false;
        for (SolderingRecipe recipe : RECIPES) {
            List<Input> group = slot < 3 ? recipe.toppings() : slot < 5 ? recipe.pcb() : recipe.solder();
            for (Input input : group) if (input.ingredient().test(stack)) return true;
        }
        return false;
    }

    private static boolean matchesGroup(ItemStack[] inputs, int from, int to, List<Input> required) {
        List<Input> remaining = new ArrayList<>(required);
        for (int slot = from; slot < to; slot++) {
            ItemStack stack = inputs[slot];
            if (stack.isEmpty()) continue;
            int match = -1;
            for (int index = 0; index < remaining.size(); index++) {
                if (remaining.get(index).matches(stack)) { match = index; break; }
            }
            if (match < 0) return false;
            remaining.remove(match);
        }
        return remaining.isEmpty();
    }

    private static Input circuit(CircuitItem.CircuitType type, int count) {
        ItemStack stack = CircuitItem.create(ModItems.CIRCUIT.get(), type, 1);
        return new Input(DataComponentIngredient.of(false, stack), count);
    }

    private static Input wire(WireFineItem.WireMaterial material, int count) {
        ItemStack stack = WireFineItem.create(ModItems.WIRE_FINE.get(), material, 1);
        Ingredient hbmSubtype = DataComponentIngredient.of(false, stack);
        TagKey<Item> compatibilityTag = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "wires/fine/" + material.id()));
        Ingredient externalCompatibility = DifferenceIngredient.of(
                Ingredient.of(compatibilityTag), Ingredient.of(ModItems.WIRE_FINE.get()));
        return new Input(CompoundIngredient.of(hbmSubtype, externalCompatibility), count);
    }

    private static Input tag(String namespace, String path, int count) {
        return new Input(Ingredient.of(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(namespace, path))), count);
    }

    public record Input(Ingredient ingredient, int count) {
        public boolean matches(ItemStack stack) { return ingredient.test(stack) && stack.getCount() >= count; }
    }

    public record SolderingRecipe(ItemStack output, int duration, long consumption,
                                  Supplier<? extends Fluid> fluid, int fluidAmount,
                                  List<Input> toppings, List<Input> pcb, List<Input> solder) {
        public SolderingRecipe(ItemStack output, int duration, long consumption,
                               List<Input> toppings, List<Input> pcb, List<Input> solder) {
            this(output, duration, consumption, null, 0, toppings, pcb, solder);
        }

        public boolean usesFluid() { return fluid != null && fluidAmount > 0; }
    }
}
