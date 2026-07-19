package com.hbm.ntm.block;

import com.hbm.ntm.HbmNtm;
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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LithiumFamilyGameTests {
    private LithiumFamilyGameTests() { }

    @GameTest(template = "empty")
    public static void lithiumFormsUseExactSourceHydroactivity(GameTestHelper helper) {
        assertHydroactivity(helper, "lithium", 1.0F);
        assertHydroactivity(helper, "powder_lithium", 3.0F);
        assertHydroactivity(helper, "powder_lithium_tiny", 0.3F);
        assertHydroactivity(helper, "block_lithium", 10.0F);

        check(helper, ModBlocks.get("block_lithium").get() instanceof HydroactiveBlock block
                        && Math.abs(block.explosionStrength() - 15.0F) < 0.0001F,
                "Lithium storage block must use the source 15-strength adjacent-water reaction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lithiumFormsExposeSourceMaterialTags(GameTestHelper helper) {
        assertTag(helper, "lithium", "c", "ingots/lithium");
        assertTag(helper, "powder_lithium", "c", "dusts/lithium");
        assertTag(helper, "powder_lithium_tiny", "hbm", "dusts/tiny/lithium");
        assertTag(helper, "block_lithium", "c", "storage_blocks/lithium");
        assertTag(helper, "ore_gneiss_lithium", "c", "ores/lithium");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lithiumAcquisitionAndConversionRecipesAreComplete(GameTestHelper helper) {
        assertCookingRecipe(helper, "lithium_from_ore_gneiss_lithium", 10.0F);
        assertCookingRecipe(helper, "lithium_from_powder", 1.0F);
        assertRecipe(helper, "lithium_block", "block_lithium", 1);
        assertRecipe(helper, "lithium_from_block_lithium", "lithium", 9);
        assertRecipe(helper, "powder_lithium", "powder_lithium", 1);
        assertRecipe(helper, "powder_lithium_tiny_from_powder", "powder_lithium_tiny", 9);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lithiumBlockDetectsAllAdjacentWaterFaces(GameTestHelper helper) {
        BlockPos center = new BlockPos(3, 3, 3);
        for (BlockPos offset : new BlockPos[]{
                new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
                new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
                new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
        }) {
            BlockPos water = center.offset(offset);
            helper.setBlock(water, Blocks.WATER);
            check(helper, HydroactiveBlock.isTouchingWater(helper.getLevel(), helper.absolutePos(center)),
                    "Lithium block must detect adjacent water at " + offset);
            helper.setBlock(water, Blocks.AIR);
        }
        check(helper, !HydroactiveBlock.isTouchingWater(helper.getLevel(), helper.absolutePos(center)),
                "Lithium block must not react without adjacent water");
        helper.succeed();
    }

    private static void assertCookingRecipe(GameTestHelper helper, String id, float experience) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id));
        ItemStack result = holder
                .map(recipe -> recipe.value().getResultItem(helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        check(helper, result.is(item("lithium")) && result.getCount() == 1,
                "Recipe hbm:" + id + " must produce one hbm:lithium");
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

    private static void assertTag(GameTestHelper helper, String id, String namespace, String path) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
        check(helper, new ItemStack(item(id)).is(tag), "hbm:" + id + " must expose source tag #" + tag.location());
    }

    private static void assertHydroactivity(GameTestHelper helper, String id, float expected) {
        Item item = item(id);
        check(helper, item != Items.AIR, "Expected registered Lithium form hbm:" + id);
        check(helper, item instanceof HazardCarrier carrier
                        && Math.abs(carrier.hbm$getHazards(new ItemStack(item)).hydroactive() - expected) < 0.0001F,
                "hbm:" + id + " must have source hydroactivity " + expected);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
