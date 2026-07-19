package com.hbm.ntm.registry;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.StoneResourceBlock;
import com.hbm.ntm.worldgen.ColtanDepositFeature;
import com.hbm.ntm.worldgen.LegacyOreLayerFeature;
import com.hbm.ntm.worldgen.OilBubbleFeature;
import com.hbm.ntm.worldgen.LegacyOverworldOreFeature;
import com.hbm.ntm.worldgen.LegacyNetherOreFeature;
import com.hbm.ntm.worldgen.LegacyEndOreFeature;
import com.hbm.ntm.worldgen.LegacySpecialDepositFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, HbmNtm.MOD_ID);

    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> OIL_BUBBLE =
            FEATURES.register("oil_bubble", OilBubbleFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> COLTAN_DEPOSIT =
            FEATURES.register("coltan_deposit", ColtanDepositFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> HEMATITE_LAYER =
            FEATURES.register("hematite_layer", () -> new LegacyOreLayerFeature(
                    StoneResourceBlock.Type.HEMATITE, 0, 0.04D, 0.25D, 230.0D));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> MALACHITE_LAYER =
            FEATURES.register("malachite_layer", () -> new LegacyOreLayerFeature(
                    StoneResourceBlock.Type.MALACHITE, 2, 0.1D, 0.15D, 275.0D));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> BAUXITE_LAYER =
            FEATURES.register("bauxite_layer", () -> new LegacyOreLayerFeature(
                    StoneResourceBlock.Type.BAUXITE, 1, 0.03D, 0.15D, 300.0D));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> LEGACY_OVERWORLD_ORES =
            FEATURES.register("legacy_overworld_ores", LegacyOverworldOreFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> LEGACY_NETHER_ORES =
            FEATURES.register("legacy_nether_ores", LegacyNetherOreFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> LEGACY_END_ORES =
            FEATURES.register("legacy_end_ores", LegacyEndOreFeature::new);
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> LEGACY_SPECIAL_DEPOSITS =
            FEATURES.register("legacy_special_deposits", LegacySpecialDepositFeature::new);

    private ModFeatures() { }

    public static void register(IEventBus eventBus) {
        FEATURES.register(eventBus);
    }
}
