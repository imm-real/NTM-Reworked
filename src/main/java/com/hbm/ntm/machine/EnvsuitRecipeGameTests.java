package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.foundry.FoundryMaterial;
import com.hbm.ntm.item.CastPlateItem;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.item.FoundryMoldItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Compile-only envsuit checks; focused GameTests are not launched automatically. */
@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnvsuitRecipeGameTests {
    private EnvsuitRecipeGameTests() {
    }

    @GameTest(template = "empty")
    public static void titaniumCastPlateRetainsSourceIdentityAndFoundryAmount(GameTestHelper helper) {
        ItemStack cast = FoundryMoldItem.Mold.CAST_PLATE.output(FoundryMaterial.TITANIUM);
        FoundryMaterial.MaterialAmount remelted = FoundryMaterial.fromItem(cast);
        CustomModelData model = cast.get(DataComponents.CUSTOM_MODEL_DATA);
        check(helper, cast.is(ModItems.PLATE_CAST.get())
                        && CastPlateItem.material(cast) == CastPlateItem.CastPlateMaterial.TITANIUM
                        && model != null && model.value() == 2200
                        && remelted != null && remelted.material() == FoundryMaterial.TITANIUM
                        && remelted.amount() == FoundryMaterial.CAST_PLATE,
                "Source plateCastTitanium must remain material titanium/meta 2200 and contain 216 units");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fourM1ttyCraftingPatternsMatchSource(GameTestHelper helper) {
        ItemStack titanium = new ItemStack(ModItems.get("plate_titanium").get());
        ItemStack rubber = new ItemStack(ModItems.get("ingot_rubber").get());
        ItemStack chip = CircuitItem.create(ModItems.CIRCUIT.get(), CircuitItem.CircuitType.CHIP, 1);
        ItemStack cast = CastPlateItem.create(ModItems.PLATE_CAST.get(),
                CastPlateItem.CastPlateMaterial.TITANIUM, 1);

        assertCrafts(helper, "envsuit_helmet", CraftingInput.of(3, 3, List.of(
                titanium.copy(), chip, titanium.copy(),
                titanium.copy(), new ItemStack(Items.GLASS_PANE), titanium.copy(),
                rubber.copy(), rubber.copy(), rubber.copy())));
        assertCrafts(helper, "envsuit_plate", CraftingInput.of(3, 3, List.of(
                titanium.copy(), ItemStack.EMPTY, titanium.copy(),
                titanium.copy(), cast.copy(), titanium.copy(),
                rubber.copy(), rubber.copy(), rubber.copy())));
        assertCrafts(helper, "envsuit_legs", CraftingInput.of(3, 3, List.of(
                titanium.copy(), cast.copy(), titanium.copy(),
                rubber.copy(), ItemStack.EMPTY, rubber.copy(),
                titanium.copy(), ItemStack.EMPTY, titanium.copy())));
        assertCrafts(helper, "envsuit_boots", CraftingInput.of(3, 2, List.of(
                rubber.copy(), ItemStack.EMPTY, rubber.copy(),
                titanium.copy(), ItemStack.EMPTY, titanium.copy())));
        helper.succeed();
    }

    private static void assertCrafts(GameTestHelper helper, String id, CraftingInput input) {
        Item expected = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input,
                helper.getLevel()).orElseThrow();
        check(helper, recipe.value().assemble(input, helper.getLevel().registryAccess()).is(expected),
                "Source M1TTY pattern must craft hbm:" + id);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
