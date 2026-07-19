package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.NukeSoliniumMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class NukeSoliniumScreen extends AbstractContainerScreen<NukeSoliniumMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/weapon/solinium_schematic.png");

    public NukeSoliniumScreen(NukeSoliniumMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelX = 8;
        // Source drew the inventory label at ySize - 96 + 2.
        inventoryLabelY = imageHeight - 96 + 2;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        // Source schematic overlays: igniter strips 22x14 at src rows 222/236, propellant 18x14,
        // core 12x28 at src (40,222). Only drawn while the correct part occupies each slot.
        if (menu.isIgniter(0)) graphics.blit(TEXTURE, leftPos + 24, topPos + 84, 0, 222, 22, 14, 256, 256);
        if (menu.isPropellant(1)) graphics.blit(TEXTURE, leftPos + 46, topPos + 84, 22, 222, 18, 14, 256, 256);
        if (menu.isPropellant(2)) graphics.blit(TEXTURE, leftPos + 76, topPos + 84, 52, 222, 18, 14, 256, 256);
        if (menu.isIgniter(3)) graphics.blit(TEXTURE, leftPos + 94, topPos + 84, 70, 222, 22, 14, 256, 256);
        if (menu.isCore(4)) graphics.blit(TEXTURE, leftPos + 64, topPos + 84, 40, 222, 12, 28, 256, 256);
        if (menu.isIgniter(5)) graphics.blit(TEXTURE, leftPos + 24, topPos + 98, 0, 236, 22, 14, 256, 256);
        if (menu.isPropellant(6)) graphics.blit(TEXTURE, leftPos + 46, topPos + 98, 22, 236, 18, 14, 256, 256);
        if (menu.isPropellant(7)) graphics.blit(TEXTURE, leftPos + 76, topPos + 98, 52, 236, 18, 14, 256, 256);
        if (menu.isIgniter(8)) graphics.blit(TEXTURE, leftPos + 94, topPos + 98, 70, 236, 22, 14, 256, 256);
        if (menu.isReady()) graphics.blit(TEXTURE, leftPos + 134, topPos + 90, 176, 0, 16, 16, 256, 256);
    }
}
