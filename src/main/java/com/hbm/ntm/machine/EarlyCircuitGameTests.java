package com.hbm.ntm.machine;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.item.CircuitItem;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EarlyCircuitGameTests {
    private EarlyCircuitGameTests() { }

    @GameTest(template = "empty")
    public static void activeCircuitVariantsPreserveLegacyIdentity(GameTestHelper helper) {
        int[] expected = {0, 19, 1, 2, 3, 4, 5, 7, 8, 9, 10, 12};
        int index = 0;
        for (CircuitItem.CircuitType type : CircuitItem.CircuitType.values()) {
            ItemStack stack = CircuitItem.create(ModItems.CIRCUIT.get(), type, 1);
            check(helper, CircuitItem.type(stack) == type && type.legacyMetadata() == expected[index++],
                    "Circuit variant must preserve source component and metadata identity: " + type.id());
            check(helper, !stack.getHoverName().getString().isBlank(), "Circuit variant must have a source name");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dependencyCompleteCircuitRecipesLoadExactOutputs(GameTestHelper helper) {
        assertRecipe(helper, "circuit_vacuum_tube_from_tungsten", CircuitItem.CircuitType.VACUUM_TUBE, 1);
        assertRecipe(helper, "circuit_vacuum_tube_from_carbon", CircuitItem.CircuitType.VACUUM_TUBE, 1);
        assertRecipe(helper, "circuit_numitron", CircuitItem.CircuitType.NUMITRON, 3);
        assertRecipe(helper, "circuit_capacitor_from_aluminium", CircuitItem.CircuitType.CAPACITOR, 2);
        assertRecipe(helper, "circuit_capacitor_from_copper", CircuitItem.CircuitType.CAPACITOR, 2);
        assertRecipe(helper, "circuit_capacitor_tantalium_from_aluminium",
                CircuitItem.CircuitType.CAPACITOR_TANTALIUM, 1);
        assertRecipe(helper, "circuit_capacitor_tantalium_from_copper",
                CircuitItem.CircuitType.CAPACITOR_TANTALIUM, 1);
        assertRecipe(helper, "circuit_pcb_from_copper", CircuitItem.CircuitType.PCB, 1);
        assertRecipe(helper, "circuit_pcb_from_gold", CircuitItem.CircuitType.PCB, 4);
        helper.succeed();
    }

    private static void assertRecipe(GameTestHelper helper, String id, CircuitItem.CircuitType type, int count) {
        ItemStack result = helper.getLevel().getRecipeManager().byKey(
                ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id)).orElseThrow().value()
                .getResultItem(helper.getLevel().registryAccess());
        check(helper, result.is(ModItems.CIRCUIT.get()) && result.getCount() == count && CircuitItem.type(result) == type,
                "Recipe hbm:" + id + " must return exact circuit identity/count");
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
