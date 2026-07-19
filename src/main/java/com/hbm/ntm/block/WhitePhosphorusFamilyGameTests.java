package com.hbm.ntm.block;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.hazard.HazardCarrier;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WhitePhosphorusFamilyGameTests {
    private WhitePhosphorusFamilyGameTests() { }

    @GameTest(template = "empty")
    public static void whitePhosphorusFormsUseExactSourceHeat(GameTestHelper helper) {
        assertHeat(helper, "ingot_phosphorus", 5.0F);
        assertHeat(helper, "block_white_phosphorus", 50.0F);

        Block block = ModBlocks.get("block_white_phosphorus").get();
        check(helper, block.getClass() == Block.class,
                "White Phosphorus storage must retain the source's behaviorless BlockHazard body");
        check(helper, block.defaultMapColor() == MapColor.STONE,
                "White Phosphorus storage must retain its source rock material color");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void whitePhosphorusFormsExposeSourceMaterialTags(GameTestHelper helper) {
        assertTag(helper, "ingot_phosphorus", "ingots/white_phosphorus");
        assertTag(helper, "block_white_phosphorus", "storage_blocks/white_phosphorus");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void whitePhosphorusStorageConversionsAreLossless(GameTestHelper helper) {
        assertRecipe(helper, "white_phosphorus_block", "block_white_phosphorus", 1);
        assertRecipe(helper, "ingot_phosphorus_from_block_white_phosphorus", "ingot_phosphorus", 9);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void netherFireOreRetainsExactOneInTenDropTable(GameTestHelper helper) {
        check(helper, ModBlocks.legacy("ore_nether_fire").get() instanceof LegacyOreBlock,
                "Expected the registered source Nether Fire ore");
        check(helper, LegacyOreBlock.netherFireDrop(0).equals("hbm:ingot_phosphorus"),
                "Nether Fire roll zero must produce White Phosphorus");
        for (int roll = 1; roll < 10; roll++) {
            check(helper, LegacyOreBlock.netherFireDrop(roll).equals("hbm:powder_fire"),
                    "Nether Fire nonzero rolls must produce Red Phosphorus Powder");
        }
        helper.succeed();
    }

    private static void assertRecipe(GameTestHelper helper, String id, String outputId, int count) {
        ItemStack result = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id))
                .map(holder -> holder.value().getResultItem(helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        check(helper, result.is(item(outputId)) && result.getCount() == count,
                "Recipe hbm:" + id + " must produce " + count + " hbm:" + outputId);
    }

    private static void assertTag(GameTestHelper helper, String id, String path) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
        check(helper, new ItemStack(item(id)).is(tag), "hbm:" + id + " must expose source tag #" + tag.location());
    }

    private static void assertHeat(GameTestHelper helper, String id, float expected) {
        Item item = item(id);
        check(helper, item != Items.AIR, "Expected registered White Phosphorus form hbm:" + id);
        check(helper, item instanceof HazardCarrier carrier
                        && Math.abs(carrier.hbm$getHazards(new ItemStack(item)).heat() - expected) < 0.0001F,
                "hbm:" + id + " must have source heat " + expected);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
