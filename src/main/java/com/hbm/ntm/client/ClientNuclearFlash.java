package com.hbm.ntm.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Five seconds of shader-safe whiteout after Torex says good morning. */
public final class ClientNuclearFlash {
    static final long FLASH_DURATION_MILLIS = 5_000L;
    private static final long RETRIGGER_GUARD_MILLIS = 1_000L;
    private static long flashStartedAt = -FLASH_DURATION_MILLIS;

    private ClientNuclearFlash() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientNuclearFlash::render);
        NeoForge.EVENT_BUS.addListener(ClientNuclearFlash::loggingOut);
    }

    public static void trigger() {
        long now = Util.getMillis();
        if (now - flashStartedAt > RETRIGGER_GUARD_MILLIS) flashStartedAt = now;
    }

    private static void render(RenderGuiEvent.Post event) {
        float brightness = brightness(Util.getMillis(), flashStartedAt);
        if (brightness <= 0.0F) return;

        int alpha = Mth.clamp(Math.round(brightness * 255.0F), 0, 255);
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | 0xFFFFFF);
    }

    static float brightness(long now, long startedAt) {
        return Mth.clamp((startedAt + FLASH_DURATION_MILLIS - now) / (float) FLASH_DURATION_MILLIS,
                0.0F, 1.0F);
    }

    private static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        flashStartedAt = -FLASH_DURATION_MILLIS;
    }
}
