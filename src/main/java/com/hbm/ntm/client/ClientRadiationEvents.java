package com.hbm.ntm.client;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.config.HbmClientConfig;
import com.hbm.ntm.radiation.RadiationSystem;
import com.hbm.ntm.registry.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientRadiationEvents {
    private static final ResourceLocation OVERLAY = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/misc/overlay_misc.png");

    private static long lastSurvey;
    private static float previousRadiation;
    private static float lastRadiation;

    private ClientRadiationEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientRadiationEvents::renderHotbar);
    }

    private static void renderHotbar(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.player.getInventory().items.stream()
                .noneMatch(stack -> stack.is(ModItems.GEIGER_COUNTER.get()))) return;

        float radiation = RadiationSystem.data(minecraft.player).radiation();
        float rate = lastRadiation - previousRadiation;
        long now = System.currentTimeMillis();
        if (now >= lastSurvey + 1_000L) {
            lastSurvey = now;
            previousRadiation = lastRadiation;
            lastRadiation = radiation;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int length = 74;
        int bar = (int) Math.min(radiation / 1_000.0F * length, length);
        int x = 16 + HbmClientConfig.GEIGER_OFFSET_HORIZONTAL.get();
        int y = graphics.guiHeight() - 20 - HbmClientConfig.GEIGER_OFFSET_VERTICAL.get();

        RenderSystem.enableBlend();
        graphics.blit(OVERLAY, x, y, 0.0F, 0.0F, 94, 18, 256, 256);
        if (bar > 0) graphics.blit(OVERLAY, x + 1, y + 1, 1.0F, 19.0F, bar, 16, 256, 256);

        if (rate >= 25.0F) {
            graphics.blit(OVERLAY, x + length + 2, y - 18, 36.0F, 36.0F, 18, 18, 256, 256);
        } else if (rate >= 10.0F) {
            graphics.blit(OVERLAY, x + length + 2, y - 18, 18.0F, 36.0F, 18, 18, 256, 256);
        } else if (rate >= 2.5F) {
            graphics.blit(OVERLAY, x + length + 2, y - 18, 0.0F, 36.0F, 18, 18, 256, 256);
        }

        String text = null;
        if (rate > 1_000.0F) text = ">1000 RAD/s";
        else if (rate >= 1.0F) text = Math.round(rate) + " RAD/s";
        else if (rate > 0.0F) text = "<1 RAD/s";
        if (text != null) graphics.drawString(minecraft.font, text, x, y - 8, 0xFF0000, false);
        RenderSystem.disableBlend();
    }
}
