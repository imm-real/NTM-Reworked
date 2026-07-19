package com.hbm.ntm.radiation;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.RadioactiveBlock;
import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.registry.ModBlocks;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThoriumFamilyGameTests {
    private ThoriumFamilyGameTests() { }

    @GameTest(template = "empty")
    public static void thoriumFormsUseExactSourceRadiation(GameTestHelper helper) {
        assertRadiation(helper, "ingot_th232", 0.1F);
        assertRadiation(helper, "billet_th232", 0.05F);
        assertRadiation(helper, "nugget_th232", 0.01F);
        assertRadiation(helper, "powder_thorium", 0.3F);
        assertRadiation(helper, "block_thorium", 1.0F);

        RadioactiveBlock block = (RadioactiveBlock) ModBlocks.get("block_thorium").get();
        check(helper, !block.radiationFog(), "Thorium-232 block must not use source RADFOG");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void thoriumCompatibilityTagsRetainEverySourceOreName(GameTestHelper helper) {
        for (String alias : List.of("thorium_232", "thorium232", "th232", "thorium")) {
            assertTag(helper, "ingot_th232", "ingots", alias);
            assertTag(helper, "billet_th232", "billets", alias);
            assertTag(helper, "nugget_th232", "nuggets", alias);
            assertTag(helper, "powder_thorium", "dusts", alias);
            assertTag(helper, "block_thorium", "storage_blocks", alias);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void thoriumAcquisitionAndConversionRecipesAreComplete(GameTestHelper helper) {
        assertRecipe(helper, "ingot_th232_from_ore_thorium", "ingot_th232", 1);
        assertRecipe(helper, "ingot_th232_from_powder", "ingot_th232", 1);
        assertRecipe(helper, "thorium_block", "block_thorium", 1);
        assertRecipe(helper, "ingot_th232_from_block_thorium", "ingot_th232", 9);
        assertRecipe(helper, "ingot_th232_from_nugget_th232", "ingot_th232", 1);
        assertRecipe(helper, "nugget_th232_from_ingot_th232", "nugget_th232", 9);
        assertRecipe(helper, "billet_th232_from_nugget_th232", "billet_th232", 1);
        assertRecipe(helper, "nugget_th232_from_billet_th232", "nugget_th232", 6);
        assertRecipe(helper, "ingot_th232_from_billet_th232", "ingot_th232", 2);
        assertRecipe(helper, "billet_th232_from_ingot_th232", "billet_th232", 3);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void thoriumBlockEmitsExactPlacedRadiation(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 2, 2);
        BlockPos absolute = helper.absolutePos(relative);
        float before = ChunkRadiationData.get(helper.getLevel()).get(absolute);
        helper.setBlock(relative, ModBlocks.get("block_thorium").get());
        helper.runAfterDelay(21, () -> {
            float after = ChunkRadiationData.get(helper.getLevel()).get(absolute);
            check(helper, after >= before + 0.1F,
                    "Thorium-232 block must add its exact 0.1 RAD/s placed emission after 20 ticks");
            helper.succeed();
        });
    }

    private static void assertRecipe(GameTestHelper helper, String id, String outputId, int count) {
        Item output = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, outputId));
        ItemStack result = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id))
                .map(holder -> holder.value().getResultItem(helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        check(helper, result.is(output) && result.getCount() == count,
                "Recipe hbm:" + id + " must produce " + count + " hbm:" + outputId);
    }

    private static void assertTag(GameTestHelper helper, String id, String folder, String material) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id));
        TagKey<Item> tag = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", folder + "/" + material));
        check(helper, new ItemStack(item).is(tag), "hbm:" + id + " must expose compatibility tag #" + tag.location());
    }

    private static void assertRadiation(GameTestHelper helper, String id, float expected) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id));
        check(helper, item != Items.AIR, "Expected registered Thorium-232 form hbm:" + id);
        check(helper, item instanceof HazardCarrier carrier
                        && Math.abs(carrier.hbm$getHazards(new ItemStack(item)).radiation() - expected) < 0.0001F,
                "hbm:" + id + " must have source radiation " + expected);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
