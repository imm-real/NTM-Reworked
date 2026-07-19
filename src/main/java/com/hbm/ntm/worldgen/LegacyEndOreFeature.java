package com.hbm.ntm.worldgen;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.registry.ModBlocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class LegacyEndOreFeature extends Feature<NoneFeatureConfiguration> {
    public LegacyEndOreFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!context.level().getLevel().dimension().equals(Level.END) || !HbmConfig.END_ORES.get()) return false;
        return LegacyOreGeneration.veins(context.level(), context.random(), context.origin().getX(),
                context.origin().getZ(), 8, 6, 0, 127,
                ModBlocks.legacy("ore_tikite").get().defaultBlockState(), state -> state.is(Blocks.END_STONE)) > 0;
    }
}
