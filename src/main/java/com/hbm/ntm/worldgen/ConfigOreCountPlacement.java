package com.hbm.ntm.worldgen;

import com.hbm.ntm.config.HbmConfig;
import com.hbm.ntm.registry.ModPlacementModifiers;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;

/** Applies the configured per-chunk ore count to a data-driven feature. */
public final class ConfigOreCountPlacement extends RepeatingPlacement {
    public static final MapCodec<ConfigOreCountPlacement> CODEC = OreType.CODEC.fieldOf("ore")
            .xmap(ConfigOreCountPlacement::new, ConfigOreCountPlacement::ore);

    private final OreType ore;

    public ConfigOreCountPlacement(OreType ore) {
        this.ore = ore;
    }

    public OreType ore() {
        return ore;
    }

    @Override
    protected int count(RandomSource random, BlockPos pos) {
        return configuredCount();
    }

    public int configuredCount() {
        if (!HbmConfig.OVERWORLD_ORES.get()) return 0;
        return switch (ore) {
            case TITANIUM -> HbmConfig.TITANIUM_SPAWN_RATE.get();
            case TUNGSTEN -> HbmConfig.TUNGSTEN_SPAWN_RATE.get();
            case COBALT -> HbmConfig.COBALT_SPAWN_RATE.get();
            case RARE_EARTH -> HbmConfig.RARE_EARTH_SPAWN_RATE.get();
        };
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModPlacementModifiers.CONFIG_ORE_COUNT.get();
    }

    public enum OreType implements StringRepresentable {
        TITANIUM("titanium"),
        TUNGSTEN("tungsten"),
        COBALT("cobalt"),
        RARE_EARTH("rare_earth");

        public static final com.mojang.serialization.Codec<OreType> CODEC =
                StringRepresentable.fromEnum(OreType::values);

        private final String id;

        OreType(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }
}
