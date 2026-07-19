package com.hbm.ntm.registry;

import com.hbm.ntm.HbmNtm;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom statistics. Ports {@code MainRegistry.statMines} ({@code stat.ntmMines}, "Mines Stepped on"): the
 * counter a player accrues by stepping on a primed landmine. In 1.21.1 a custom stat is a
 * {@link Registries#CUSTOM_STAT} entry, awarded through {@code Stats.CUSTOM.get(id)}; its display name is the
 * auto-derived {@code stat.hbm.mines} translation key.
 */
public final class ModStats {
    public static final DeferredRegister<ResourceLocation> STATS =
            DeferredRegister.create(Registries.CUSTOM_STAT, HbmNtm.MOD_ID);

    public static final ResourceLocation MINES =
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "mines");

    static {
        STATS.register("mines", () -> MINES);
    }

    private ModStats() {
    }

    public static void register(IEventBus modEventBus) {
        STATS.register(modEventBus);
    }
}
