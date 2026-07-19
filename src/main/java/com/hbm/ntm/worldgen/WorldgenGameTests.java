package com.hbm.ntm.worldgen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.StoneResourceBlock;
import com.hbm.ntm.blockentity.OilDerrickBlockEntity;
import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.registry.ModFeatures;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HbmNtm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldgenGameTests {
    private WorldgenGameTests() {
    }

    @GameTest(template = "empty")
    public static void sourceDepthsTrackTheModernDimensionBottom(GameTestHelper helper) {
        check(helper, LegacyWorldgenHeights.aboveBottom(-64, 5) == -59
                        && LegacyWorldgenHeights.aboveBottom(-64, 34) == -30
                        && LegacyWorldgenHeights.aboveBottom(-64, 4) == -60
                        && LegacyWorldgenHeights.aboveBottom(-64, 11) == -53
                        && LegacyWorldgenHeights.aboveBottom(-64, OilDerrickBlockEntity.DRILL_DEPTH) == -59,
                "Legacy ore bands must move below zero with the 1.21.1 Overworld bottom");
        check(helper, LegacyWorldgenHeights.aboveBottom(helper.getLevel(), 5)
                        == helper.getLevel().getMinBuildHeight() + 5,
                "Worldgen depth conversion must use the active dimension's minimum build height");
        check(helper, LegacyOreLayerFeature.MIN_Y_ABOVE_BOTTOM == 6
                        && LegacyOreLayerFeature.MAX_Y_ABOVE_BOTTOM == 64,
                "OreLayer3D must retain source Y 6-64 as distances above bottom");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stoneResourceLayersKeepSourceSettings(GameTestHelper helper) {
        checkLayer(helper, ModFeatures.HEMATITE_LAYER.get(), "hematite_layer",
                StoneResourceBlock.Type.HEMATITE, 0, 0.04D, 0.25D, 230.0D);
        checkLayer(helper, ModFeatures.MALACHITE_LAYER.get(), "malachite_layer",
                StoneResourceBlock.Type.MALACHITE, 2, 0.1D, 0.15D, 275.0D);
        checkLayer(helper, ModFeatures.BAUXITE_LAYER.get(), "bauxite_layer",
                StoneResourceBlock.Type.BAUXITE, 1, 0.03D, 0.15D, 300.0D);
        check(helper, HbmConfig.HEMATITE_DEPOSITS.getDefault()
                        && HbmConfig.MALACHITE_DEPOSITS.getDefault()
                        && HbmConfig.BAUXITE_DEPOSITS.getDefault(),
                "All three source layer-deposit toggles must default to enabled");
        check(helper, StoneResourceBlock.Type.values().length == 6
                        && StoneResourceBlock.Type.SULFUR.legacyMetadata() == 0
                        && StoneResourceBlock.Type.ASBESTOS.legacyMetadata() == 1
                        && StoneResourceBlock.Type.LIMESTONE.legacyMetadata() == 4
                        && StoneResourceBlock.Type.BAUXITE.legacyMetadata() == 5,
                "Stone resources must preserve all source metadata IDs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void completeLegacyOreFamiliesAreRegistered(GameTestHelper helper) {
        for (String id : new String[]{"ore_uranium", "ore_thorium", "ore_sulfur", "ore_aluminium",
                "cluster_iron", "stone_gneiss", "ore_gneiss_lithium", "stone_depth",
                "cluster_depth_tungsten", "ore_depth_nether_neodymium", "ore_nether_uranium",
                "ore_nether_plutonium", "ore_tikite", "ore_oil_sand", "ore_bedrock_oil", "ore_bedrock"}) {
            check(helper, ModBlocks.LEGACY_ORE_BLOCKS.containsKey(id), "Missing legacy ore block hbm:" + id);
        }
        check(helper, HbmConfig.GAS_BUBBLE_RATE.getDefault() == 12
                        && HbmConfig.ALEXANDRITE_RATE.getDefault() == 100
                        && HbmConfig.BEDROCK_OIL_RATE.getDefault() == 200,
                "Special deposits must retain their source default frequencies");
        helper.succeed();
    }

    private static void checkLayer(GameTestHelper helper, Object feature, String id,
                                   StoneResourceBlock.Type type, int sourceLayerId,
                                   double horizontalScale, double verticalScale, double threshold) {
        check(helper, feature instanceof LegacyOreLayerFeature,
                "hbm:" + id + " must use the source-compatible layer feature");
        LegacyOreLayerFeature layer = (LegacyOreLayerFeature) feature;
        check(helper, type == layer.resourceType()
                        && sourceLayerId == layer.sourceLayerId()
                        && horizontalScale == layer.horizontalScale()
                        && verticalScale == layer.verticalScale()
                        && threshold == layer.threshold(),
                "hbm:" + id + " must preserve its source OreLayer3D constants");
        check(helper, ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, id)
                        .equals(BuiltInRegistries.FEATURE.getKey(layer)),
                "hbm:" + id + " must be registered under its configured-feature type ID");
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
