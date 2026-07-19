package com.hbm.ntm.registry;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.worldgen.ConfigOreCountPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModPlacementModifiers {
    public static final DeferredRegister<PlacementModifierType<?>> TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, HbmNtm.MOD_ID);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ConfigOreCountPlacement>>
            CONFIG_ORE_COUNT = TYPES.register("config_ore_count", () -> () -> ConfigOreCountPlacement.CODEC);

    private ModPlacementModifiers() { }

    public static void register(IEventBus eventBus) {
        TYPES.register(eventBus);
    }
}
