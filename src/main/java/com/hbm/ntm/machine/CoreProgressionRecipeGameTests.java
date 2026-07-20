package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.BreedingRodItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreProgressionRecipeGameTests {
    private CoreProgressionRecipeGameTests() { }

    @GameTest(template = "empty")
    public static void denseStoneMatchesTheSourceRecipe(GameTestHelper helper) {
        craft(helper, "reinforced_stone", ModItems.REINFORCED_STONE_ITEM.get(), 4, Map.of(
                'F', new ItemStack(Items.COBBLESTONE),
                'B', new ItemStack(Items.STONE)), "FBF", "BFB", "FBF");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyBreedingRodsKeepEverySourceConversion(GameTestHelper helper) {
        ItemStack single = craft(helper, "rod_empty", ModItems.ROD_EMPTY.get(), 16, Map.of(
                'S', new ItemStack(ModItems.get("plate_steel").get()),
                'L', new ItemStack(ModItems.get("plate_lead").get())), "SSS", "L L", "SSS");
        ItemStack dual = craft(helper, "rod_dual_empty_from_rods", ModItems.ROD_DUAL_EMPTY.get(), 1,
                Map.of('R', single.copyWithCount(1)), "RR");
        craft(helper, "rod_empty_from_dual", ModItems.ROD_EMPTY.get(), 2,
                Map.of('D', dual.copy()), "D");

        ItemStack quadFromSingles = craft(helper, "rod_quad_empty_from_rods", ModItems.ROD_QUAD_EMPTY.get(), 1,
                Map.of('R', single.copyWithCount(1)), "RR", "RR");
        craft(helper, "rod_empty_from_quad", ModItems.ROD_EMPTY.get(), 4,
                Map.of('Q', quadFromSingles.copy()), "Q");
        craft(helper, "rod_quad_empty_from_duals", ModItems.ROD_QUAD_EMPTY.get(), 1,
                Map.of('D', dual.copy()), "DD");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wasteDrumUsesTheSourceRodAndLeadShell(GameTestHelper helper) {
        craft(helper, "machine_waste_drum", ModItems.MACHINE_WASTE_DRUM_ITEM.get(), 1, Map.of(
                'L', new ItemStack(ModItems.get("ingot_lead").get()),
                'B', new ItemStack(Items.IRON_BARS),
                'R', new ItemStack(ModItems.ROD_QUAD_EMPTY.get())), "LRL", "BRB", "LRL");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void activeBreedingRodsKeepSourceLoadingAndUnloading(GameTestHelper helper) {
        List<RodMaterial> materials = List.of(
                new RodMaterial(BreedingRodItem.Type.LITHIUM, "lithium"),
                new RodMaterial(BreedingRodItem.Type.TH232, "billet_th232"),
                new RodMaterial(BreedingRodItem.Type.U235, "billet_u235"),
                new RodMaterial(BreedingRodItem.Type.U238, "billet_u238"),
                new RodMaterial(BreedingRodItem.Type.URANIUM, "billet_uranium")
        );

        for (RodMaterial material : materials) {
            for (BreedingRodItem.Form form : BreedingRodItem.Form.values()) {
                int amount = switch (form) {
                    case SINGLE -> 1;
                    case DUAL -> 2;
                    case QUAD -> 4;
                };
                Item emptyRod = emptyRod(form);
                Item loadedRod = loadedRod(form);
                Item filling = ModItems.get(material.itemId()).get();

                List<ItemStack> loadingInputs = new ArrayList<>();
                loadingInputs.add(new ItemStack(emptyRod));
                for (int i = 0; i < amount; i++) loadingInputs.add(new ItemStack(filling));
                CraftingInput loading = craftingInput(loadingInputs);
                var loadingRecipe = helper.getLevel().getRecipeManager().getRecipeFor(
                        RecipeType.CRAFTING, loading, helper.getLevel()).orElseThrow();
                check(helper, loadingRecipe.id().equals(id(form.id() + "_" + material.type().id())),
                        "Loading recipe must keep the source rod/form identity");
                ItemStack filled = loadingRecipe.value().assemble(loading, helper.getLevel().registryAccess());
                check(helper, filled.is(loadedRod) && BreedingRodItem.type(filled) == material.type(),
                        "Loading must preserve " + material.type().id() + " in the " + form.id());
                check(helper, loadingRecipe.value().getRemainingItems(loading).stream().allMatch(ItemStack::isEmpty),
                        "Loading an empty rod must not duplicate its shell");

                CraftingInput unloading = craftingInput(List.of(filled.copy()));
                var unloadingRecipe = helper.getLevel().getRecipeManager().getRecipeFor(
                        RecipeType.CRAFTING, unloading, helper.getLevel()).orElseThrow();
                String formSuffix = switch (form) {
                    case SINGLE -> "";
                    case DUAL -> "_dual";
                    case QUAD -> "_quad";
                };
                check(helper, unloadingRecipe.id().equals(id(material.itemId() + "_from_rod" + formSuffix)),
                        "Unloading recipe must keep the source material/form identity");
                ItemStack returned = unloadingRecipe.value().assemble(
                        unloading, helper.getLevel().registryAccess());
                check(helper, returned.is(filling) && returned.getCount() == amount,
                        "Unloading must return exactly " + amount + " source material item(s)");
                List<ItemStack> remainders = unloadingRecipe.value().getRemainingItems(unloading);
                check(helper, remainders.size() == 1 && remainders.getFirst().is(emptyRod),
                        "Unloading must return the matching empty " + form.id());
            }
        }
        helper.succeed();
    }

    private static CraftingInput craftingInput(List<ItemStack> ingredients) {
        int width = Math.min(3, ingredients.size());
        int height = (ingredients.size() + width - 1) / width;
        List<ItemStack> slots = new ArrayList<>(ingredients);
        while (slots.size() < width * height) slots.add(ItemStack.EMPTY);
        return CraftingInput.of(width, height, slots);
    }

    private static Item emptyRod(BreedingRodItem.Form form) {
        return switch (form) {
            case SINGLE -> ModItems.ROD_EMPTY.get();
            case DUAL -> ModItems.ROD_DUAL_EMPTY.get();
            case QUAD -> ModItems.ROD_QUAD_EMPTY.get();
        };
    }

    private static Item loadedRod(BreedingRodItem.Form form) {
        return switch (form) {
            case SINGLE -> ModItems.ROD.get();
            case DUAL -> ModItems.ROD_DUAL.get();
            case QUAD -> ModItems.ROD_QUAD.get();
        };
    }

    private static ItemStack craft(GameTestHelper helper, String recipeName, Item expected, int count,
                                   Map<Character, ItemStack> key, String... pattern) {
        int width = pattern[0].length();
        var slots = new ArrayList<ItemStack>(width * pattern.length);
        for (String row : pattern) {
            check(helper, row.length() == width, "Test pattern " + recipeName + " must be rectangular");
            for (int column = 0; column < width; column++) {
                char symbol = row.charAt(column);
                slots.add(symbol == ' ' ? ItemStack.EMPTY : key.get(symbol).copy());
            }
        }
        CraftingInput input = CraftingInput.of(width, pattern.length, slots);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(
                RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        check(helper, recipe.id().equals(id(recipeName)), "Crafting grid must resolve to hbm:" + recipeName);
        ItemStack output = recipe.value().assemble(input, helper.getLevel().registryAccess());
        check(helper, output.is(expected) && output.getCount() == count,
                "hbm:" + recipeName + " must produce " + count + " source item(s)");
        return output;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, path);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    private record RodMaterial(BreedingRodItem.Type type, String itemId) { }
}
