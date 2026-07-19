package com.hbm.ntm.registry;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.effect.BangMobEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, HbmNtm.MOD_ID);

    public static final DeferredHolder<MobEffect, BangMobEffect> BANG =
            EFFECTS.register("bang", BangMobEffect::new);

    private ModEffects() { }

    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}
