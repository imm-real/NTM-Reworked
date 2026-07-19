package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.SteelFurnaceBlockEntity;
import com.hbm.ntm.inventory.SteelFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;
import java.util.Locale;

public final class SteelFurnaceScreen extends AbstractContainerScreen<SteelFurnaceMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_furnace_steel.png");
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    public SteelFurnaceScreen(SteelFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelX = 8;
        inventoryLabelY = 72;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        for (int lane = 0; lane < SteelFurnaceBlockEntity.LANE_COUNT; lane++) {
            if (isHovering(53, 17 + 18 * lane, 70, 7, mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.literal(FORMAT.format(menu.progress(lane)) + " / "
                        + FORMAT.format(SteelFurnaceBlockEntity.PROCESS_TIME) + "TU"), mouseX, mouseY);
                return;
            }
            if (isHovering(53, 26 + 18 * lane, 70, 7, mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.literal("Bonus: " + menu.bonus(lane) + "%"), mouseX, mouseY);
                return;
            }
        }
        if (isHovering(151, 18, 9, 50, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal(FORMAT.format(menu.heat()) + " / "
                    + FORMAT.format(SteelFurnaceBlockEntity.MAX_HEAT) + "TU"), mouseX, mouseY);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int heatHeight = menu.heat() * 48 / SteelFurnaceBlockEntity.MAX_HEAT;
        if (heatHeight > 0) graphics.blit(TEXTURE, leftPos + 152, topPos + 67 - heatHeight,
                176, 76 - heatHeight, 7, heatHeight, 256, 256);
        for (int lane = 0; lane < SteelFurnaceBlockEntity.LANE_COUNT; lane++) {
            int progressWidth = menu.progress(lane) * 69 / SteelFurnaceBlockEntity.PROCESS_TIME;
            if (progressWidth > 0) graphics.blit(TEXTURE, leftPos + 54, topPos + 18 + 18 * lane,
                    176, 18, progressWidth, 5, 256, 256);
            int bonusWidth = menu.bonus(lane) * 69 / 100;
            if (bonusWidth > 0) graphics.blit(TEXTURE, leftPos + 54, topPos + 27 + 18 * lane,
                    176, 23, bonusWidth, 5, 256, 256);
            if (menu.wasOn()) graphics.blit(TEXTURE, leftPos + 16, topPos + 16 + 18 * lane,
                    176, 0, 18, 18, 256, 256);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
