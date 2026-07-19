package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.BoltItem;
import com.hbm.ntm.item.CastPlateItem;
import com.hbm.ntm.item.WireFineItem;
import com.hbm.ntm.recipe.PressRecipes;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FineWireGameTests {
    private FineWireGameTests() { }

    @GameTest(template = "empty")
    public static void wireVariantsPreserveSourceMetadataAndNames(GameTestHelper helper) {
        int[] expected = {699, 7900, 2900, 7400, 1300, 8200, 4000, 30, 31};
        int index = 0;
        for (WireFineItem.WireMaterial material : WireFineItem.WireMaterial.values()) {
            ItemStack stack = WireFineItem.create(ModItems.WIRE_FINE.get(), material, 1);
            check(helper, WireFineItem.material(stack) == material,
                    "Fine wire must preserve material " + material.id());
            check(helper, material.legacyMetadata() == expected[index++],
                    "Fine wire must preserve source material metadata");
            check(helper, stack.getHoverName().getString().contains("Wire"),
                    "Fine wire variant must expose its material name");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void burnerPressProducesEightExactFineWires(GameTestHelper helper) {
        ItemStack stamp = new ItemStack(ModItems.STAMPS.get("stamp_stone_wire").get());
        for (WireFineItem.WireMaterial material : WireFineItem.WireMaterial.values()) {
            ItemStack input = switch (material) {
                case GOLD -> new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT);
                default -> new ItemStack(ModItems.get(material.ingotItem().substring("hbm:".length())).get());
            };
            ItemStack output = PressRecipes.getOutput(input, stamp);
            check(helper, output.is(ModItems.WIRE_FINE.get()) && output.getCount() == 8
                            && WireFineItem.material(output) == material,
                    "Wire stamp must press one " + material.id() + " ingot into eight matching wires");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void boundedWireAndComponentRecipesLoadWithExactOutputs(GameTestHelper helper) {
        assertRecipe(helper, "wire_fine_red_copper", ModItems.WIRE_FINE.get(), 24,
                WireFineItem.WireMaterial.RED_COPPER);
        for (BoltItem.BoltMaterial material : BoltItem.BoltMaterial.values()) {
            ItemStack boltResult = helper.getLevel().getRecipeManager().byKey(
                    ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "bolt_" + material.id())).orElseThrow().value()
                    .getResultItem(helper.getLevel().registryAccess());
            check(helper, boltResult.is(ModItems.BOLT.get()) && boltResult.getCount() == 16
                            && BoltItem.material(boltResult) == material,
                    "Two vertical " + material.id() + " Ingots must produce sixteen exact source-metadata Bolts");
        }
        ItemStack ironCastPlate = helper.getLevel().getRecipeManager().byKey(
                ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "plate_cast_iron")).orElseThrow().value()
                .getResultItem(helper.getLevel().registryAccess());
        check(helper, ironCastPlate.is(ModItems.PLATE_CAST.get()) && ironCastPlate.getCount() == 1
                        && CastPlateItem.material(ironCastPlate) == CastPlateItem.CastPlateMaterial.IRON,
                "Six Steel Bolts and three Iron Plates must preserve the exact Cast Iron Plate bootstrap");
        assertRecipe(helper, "red_copper_ingot_from_wire_fine", ModItems.get("ingot_red_copper").get(), 1, null);
        assertRecipe(helper, "coil_copper_from_iron", ModItems.COIL_COPPER.get(), 1, null);
        assertRecipe(helper, "coil_copper_torus_from_steel", ModItems.COIL_COPPER_TORUS.get(), 2, null);
        assertRecipe(helper, "tank_steel", ModItems.TANK_STEEL.get(), 2, null);
        assertRecipe(helper, "motor_from_iron", ModItems.MOTOR.get(), 2, null);
        assertRecipe(helper, "machine_battery_socket_from_coil", ModItems.MACHINE_BATTERY_SOCKET_ITEM.get(), 1, null);
        check(helper, helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                        HbmNtm.MOD_ID, "steel_ingot_from_wire_fine")).isEmpty(),
                "Steel wire must preserve the source omission of a crafting-grid reverse recipe");
        helper.succeed();
    }

    private static void assertRecipe(GameTestHelper helper, String id, net.minecraft.world.item.Item item,
                                     int count, WireFineItem.WireMaterial material) {
        ItemStack result = helper.getLevel().getRecipeManager().byKey(
                ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id)).orElseThrow().value()
                .getResultItem(helper.getLevel().registryAccess());
        check(helper, result.is(item) && result.getCount() == count,
                "Recipe hbm:" + id + " must return exact output count " + count);
        if (material != null) check(helper, WireFineItem.material(result) == material,
                "Recipe hbm:" + id + " must preserve wire material identity");
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
