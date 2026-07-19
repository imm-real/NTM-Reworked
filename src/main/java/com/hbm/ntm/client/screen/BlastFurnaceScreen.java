package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.BlastFurnaceBlockEntity;
import com.hbm.ntm.inventory.BlastFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

/** Blast furnace pixels fed by synchronized menu data. */
public final class BlastFurnaceScreen extends AbstractContainerScreen<BlastFurnaceMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_blast_furnace.png");
    private static final ResourceLocation AIR = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/fluids/airblast.png");
    private static final ResourceLocation FLUE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/fluids/flue.png");

    public BlastFurnaceScreen(BlastFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelX = 8;
        inventoryLabelY = 128;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(79, 62, 18, 18, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal("Speed: " + (int) (menu.speed() * 100D) + "%"),
                    mouseX, mouseY);
        } else if (isHovering(25, 71, 18, 18, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.translatable("hbmfluid.airblast"),
                    Component.literal(menu.air() + "/" + BlastFurnaceBlockEntity.AIR_CAPACITY + "mB")),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(25, 17, 18, 18, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.translatable("hbmfluid.flue"),
                    Component.literal(menu.flue() + "/" + BlastFurnaceBlockEntity.FLUE_CAPACITY + "mB")),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int fuelHeight = (int) Math.round(menu.fuel() * 26D / BlastFurnaceBlockEntity.MAX_FUEL);
        int progressHeight = (int) Math.round(menu.progress() * (88D - fuelHeight));
        if (progressHeight > 0) graphics.blit(TEXTURE,
                leftPos + 62, topPos + 106 - progressHeight - fuelHeight,
                176, 102 - progressHeight - fuelHeight, 56, progressHeight, 256, 256);
        if (fuelHeight > 0) graphics.blit(TEXTURE,
                leftPos + 62, topPos + 106 - fuelHeight,
                176, 128 - fuelHeight, 56, fuelHeight, 256, 256);
        if (menu.progressing()) {
            graphics.blit(TEXTURE, leftPos + 81, topPos + 64, 176, 0, 14, 14, 256, 256);
        }
        drawGauge(graphics, AIR, menu.air(), BlastFurnaceBlockEntity.AIR_CAPACITY,
                leftPos + 34, topPos + 80);
        drawGauge(graphics, FLUE, menu.flue(), BlastFurnaceBlockEntity.FLUE_CAPACITY,
                leftPos + 34, topPos + 26);
    }

    private static void drawGauge(GuiGraphics graphics, ResourceLocation texture, int amount, int capacity,
                                  int x, int bottom) {
        int height = Math.max(0, Math.min(16, amount * 16 / capacity));
        if (height > 0) graphics.blit(texture, x, bottom - height, 0, 16 - height, 5, height, 16, 16);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
