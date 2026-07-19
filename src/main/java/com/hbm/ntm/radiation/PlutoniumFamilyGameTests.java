package com.hbm.ntm.radiation;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.RadioactiveBlock;
import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.registry.ModBlocks;
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
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlutoniumFamilyGameTests {
    private PlutoniumFamilyGameTests() { }

    @GameTest(template = "empty")
    public static void plutoniumFormsUseExactSourceRadiation(GameTestHelper helper) {
        assertRadiation(helper, "ingot_plutonium", 7.5F);
        assertRadiation(helper, "billet_plutonium", 3.75F);
        assertRadiation(helper, "nugget_plutonium", 0.75F);
        assertRadiation(helper, "powder_plutonium", 22.5F);
        assertRadiation(helper, "block_plutonium", 75.0F);

        RadioactiveBlock block = (RadioactiveBlock) ModBlocks.get("block_plutonium").get();
        check(helper, block.radiationFog(), "Generic Plutonium block must retain source RADFOG");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plutoniumFormsExposeSourceMaterialTags(GameTestHelper helper) {
        assertTag(helper, "ingot_plutonium", "ingots");
        assertTag(helper, "billet_plutonium", "billets");
        assertTag(helper, "nugget_plutonium", "nuggets");
        assertTag(helper, "powder_plutonium", "dusts");
        assertTag(helper, "block_plutonium", "storage_blocks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plutoniumAcquisitionAndConversionRecipesAreComplete(GameTestHelper helper) {
        assertCookingRecipe(helper, "ingot_plutonium_from_ore_nether_plutonium", 24.0F);
        assertCookingRecipe(helper, "ingot_plutonium_from_powder", 1.0F);
        assertRecipe(helper, "plutonium_block", "block_plutonium", 1);
        assertRecipe(helper, "ingot_plutonium_from_block_plutonium", "ingot_plutonium", 9);
        assertRecipe(helper, "ingot_plutonium_from_nugget_plutonium", "ingot_plutonium", 1);
        assertRecipe(helper, "nugget_plutonium_from_ingot_plutonium", "nugget_plutonium", 9);
        assertRecipe(helper, "billet_plutonium_from_nugget_plutonium", "billet_plutonium", 1);
        assertRecipe(helper, "nugget_plutonium_from_billet_plutonium", "nugget_plutonium", 6);
        assertRecipe(helper, "ingot_plutonium_from_billet_plutonium", "ingot_plutonium", 2);
        assertRecipe(helper, "billet_plutonium_from_ingot_plutonium", "billet_plutonium", 3);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "plutonium_radiation_isolated")
    public static void plutoniumBlockEmitsExactPlacedRadiation(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 2, 2);
        BlockPos absolute = helper.absolutePos(relative);
        float before = ChunkRadiationData.get(helper.getLevel()).get(absolute);
        helper.setBlock(relative, ModBlocks.get("block_plutonium").get());
        helper.runAfterDelay(21, () -> {
            float after = ChunkRadiationData.get(helper.getLevel()).get(absolute);
            check(helper, after >= before + 7.5F,
                    "Generic Plutonium block must add its exact 7.5 RAD/s placed emission after 20 ticks");
            helper.succeed();
        });
    }

    private static void assertCookingRecipe(GameTestHelper helper, String id, float experience) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id));
        ItemStack result = holder
                .map(recipe -> recipe.value().getResultItem(helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        check(helper, result.is(item("ingot_plutonium")) && result.getCount() == 1,
                "Recipe hbm:" + id + " must produce one hbm:ingot_plutonium");
        check(helper, holder.isPresent() && holder.get().value() instanceof AbstractCookingRecipe cooking
                        && Math.abs(cooking.getExperience() - experience) < 0.0001F,
                "Recipe hbm:" + id + " must retain source experience " + experience);
    }

    private static void assertRecipe(GameTestHelper helper, String id, String outputId, int count) {
        ItemStack result = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id))
                .map(holder -> holder.value().getResultItem(helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        check(helper, result.is(item(outputId)) && result.getCount() == count,
                "Recipe hbm:" + id + " must produce " + count + " hbm:" + outputId);
    }

    private static void assertTag(GameTestHelper helper, String id, String folder) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", folder + "/plutonium"));
        check(helper, new ItemStack(item(id)).is(tag), "hbm:" + id + " must expose source tag #" + tag.location());
    }

    private static void assertRadiation(GameTestHelper helper, String id, float expected) {
        Item item = item(id);
        check(helper, item != Items.AIR, "Expected registered Plutonium form hbm:" + id);
        check(helper, item instanceof HazardCarrier carrier
                        && Math.abs(carrier.hbm$getHazards(new ItemStack(item)).radiation() - expected) < 0.0001F,
                "hbm:" + id + " must have source radiation " + expected);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
