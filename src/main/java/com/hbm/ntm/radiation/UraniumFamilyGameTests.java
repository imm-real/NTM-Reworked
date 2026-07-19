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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UraniumFamilyGameTests {
    private UraniumFamilyGameTests() { }

    @GameTest(template = "empty")
    public static void uraniumFormsUseExactSourceRadiation(GameTestHelper helper) {
        assertRadiation(helper, "ingot_uranium", 0.35F);
        assertRadiation(helper, "billet_uranium", 0.175F);
        assertRadiation(helper, "nugget_uranium", 0.035F);
        assertRadiation(helper, "powder_uranium", 1.05F);
        assertRadiation(helper, "ingot_u233", 5.0F);
        assertRadiation(helper, "billet_u233", 2.5F);
        assertRadiation(helper, "nugget_u233", 0.5F);
        assertRadiation(helper, "ingot_u235", 1.0F);
        assertRadiation(helper, "billet_u235", 0.5F);
        assertRadiation(helper, "nugget_u235", 0.1F);
        assertRadiation(helper, "ingot_u238", 0.25F);
        assertRadiation(helper, "billet_u238", 0.125F);
        assertRadiation(helper, "nugget_u238", 0.025F);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyFissileUraniumBlocksUseSourceRadiationFog(GameTestHelper helper) {
        check(helper, !((RadioactiveBlock) ModBlocks.get("block_uranium").get()).radiationFog(),
                "Natural uranium must not use RADFOG");
        check(helper, ((RadioactiveBlock) ModBlocks.get("block_u233").get()).radiationFog(),
                "U-233 must use RADFOG");
        check(helper, ((RadioactiveBlock) ModBlocks.get("block_u235").get()).radiationFog(),
                "U-235 must use RADFOG");
        check(helper, !((RadioactiveBlock) ModBlocks.get("block_u238").get()).radiationFog(),
                "U-238 must not use RADFOG");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void uraniumIsotopeCompatibilityAliasesMatchSourceNames(GameTestHelper helper) {
        assertAliasFamily(helper, "u233", "uranium233", "u233");
        assertAliasFamily(helper, "u235", "uranium235", "u235");
        assertAliasFamily(helper, "u238", "uranium238", "u238");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "u233_radiation_isolated")
    public static void u233BlockEmitsExactPlacedRadiation(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 2, 2);
        BlockPos absolute = helper.absolutePos(relative);
        float before = ChunkRadiationData.get(helper.getLevel()).get(absolute);
        helper.setBlock(relative, ModBlocks.get("block_u233").get());
        helper.runAfterDelay(21, () -> {
            float after = ChunkRadiationData.get(helper.getLevel()).get(absolute);
            check(helper, after >= before + 5.0F,
                    "U-233 block must add its exact 5 RAD/s placed emission after 20 ticks");
            helper.succeed();
        });
    }

    private static void assertAliasFamily(GameTestHelper helper, String itemSuffix,
                                          String longAlias, String shortAlias) {
        assertItemAlias(helper, "ingot_" + itemSuffix, "ingots", longAlias, shortAlias);
        assertItemAlias(helper, "billet_" + itemSuffix, "billets", longAlias, shortAlias);
        assertItemAlias(helper, "nugget_" + itemSuffix, "nuggets", longAlias, shortAlias);
        assertItemAlias(helper, "block_" + itemSuffix, "storage_blocks", longAlias, shortAlias);
    }

    private static void assertItemAlias(GameTestHelper helper, String itemId, String folder, String... aliases) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, itemId));
        for (String alias : aliases) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath("c", folder + "/" + alias));
            check(helper, new ItemStack(item).is(tag), "hbm:" + itemId + " must expose compatibility tag #" + tag.location());
        }
    }

    private static void assertRadiation(GameTestHelper helper, String id, float expected) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id));
        check(helper, item != null && item != net.minecraft.world.item.Items.AIR,
                "Expected registered uranium form hbm:" + id);
        check(helper, item instanceof HazardCarrier carrier
                        && Math.abs(carrier.hbm$getHazards(new ItemStack(item)).radiation() - expected) < 0.0001F,
                "hbm:" + id + " must have source radiation " + expected);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
