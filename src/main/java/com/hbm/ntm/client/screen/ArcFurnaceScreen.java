package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.ArcFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;
import java.util.Locale;

public final class ArcFurnaceScreen extends AbstractContainerScreen<ArcFurnaceMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_arc_furnace.png");
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    public ArcFurnaceScreen(ArcFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 256;
        inventoryLabelX = 8;
        inventoryLabelY = 162;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(8, 36, 7, 70, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal(FORMAT.format(menu.power()) + " / "
                    + FORMAT.format(menu.maxPower()) + " HE"), mouseX, mouseY);
        } else if (isHovering(17, 36, 7, 70, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal(Math.round(menu.progress() * 100F) + "%"),
                    mouseX, mouseY);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        if (menu.progressing()) graphics.blit(TEXTURE, leftPos + 7, topPos + 17,
                190, 0, 18, 18, 256, 256);
        int powerHeight = menu.maxPower() <= 0L ? 0 : Mth.clamp((int) (menu.power() * 70L / menu.maxPower()), 0, 70);
        if (powerHeight > 0) graphics.blit(TEXTURE, leftPos + 8, topPos + 106 - powerHeight,
                176, 70 - powerHeight, 7, powerHeight, 256, 256);
        int progressHeight = Mth.clamp((int) (menu.progress() * 70F), 0, 70);
        if (progressHeight > 0) graphics.blit(TEXTURE, leftPos + 17, topPos + 106 - progressHeight,
                183, 70 - progressHeight, 7, progressHeight, 256, 256);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0xFFFFFF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
