package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.NukeFleijaMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class NukeFleijaScreen extends AbstractContainerScreen<NukeFleijaMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/weapon/fleija_schematic.png");

    public NukeFleijaScreen(NukeFleijaMenu menu, Inventory inventory, Component title) {
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
        if (menu.isIgniter(0)) graphics.blit(TEXTURE, leftPos + 7, topPos + 88, 176, 0, 30, 20, 256, 256);
        if (menu.isIgniter(1)) graphics.blit(TEXTURE, leftPos + 139, topPos + 88, 206, 0, 30, 20, 256, 256);
        if (menu.isPropellant(2)) graphics.blit(TEXTURE, leftPos + 57, topPos + 77, 176, 62, 18, 14, 256, 256);
        if (menu.isPropellant(3)) graphics.blit(TEXTURE, leftPos + 57, topPos + 91, 176, 76, 18, 14, 256, 256);
        if (menu.isPropellant(4)) graphics.blit(TEXTURE, leftPos + 57, topPos + 105, 176, 90, 18, 14, 256, 256);
        if (menu.isCore(5)) graphics.blit(TEXTURE, leftPos + 85, topPos + 77, 176, 20, 18, 15, 256, 256);
        if (menu.isCore(6)) graphics.blit(TEXTURE, leftPos + 103, topPos + 77, 194, 20, 18, 15, 256, 256);
        if (menu.isCore(7)) graphics.blit(TEXTURE, leftPos + 85, topPos + 92, 176, 35, 18, 12, 256, 256);
        if (menu.isCore(8)) graphics.blit(TEXTURE, leftPos + 103, topPos + 92, 194, 35, 18, 12, 256, 256);
        if (menu.isCore(9)) graphics.blit(TEXTURE, leftPos + 85, topPos + 104, 176, 47, 18, 15, 256, 256);
        if (menu.isCore(10)) graphics.blit(TEXTURE, leftPos + 103, topPos + 104, 194, 47, 18, 15, 256, 256);
    }
}
