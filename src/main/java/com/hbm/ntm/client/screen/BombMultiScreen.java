package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.BombMultiMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Bomb mode indicator. Mismatched ingredients get the idiot light. */
public final class BombMultiScreen extends AbstractContainerScreen<BombMultiMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/weapon/bomb_generic.png");

    public BombMultiScreen(BombMultiMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelX = 8;
        inventoryLabelY = 72;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        int type2 = menu.return2type();
        int type5 = menu.return5type();
        if (type2 == type5) {
            if (type2 >= 1 && type2 <= 6) {
                graphics.blit(TEXTURE, leftPos + 124, topPos + 34, 176, (type2 - 1) * 18, 18, 18, 256, 256);
            }
        } else {
            graphics.blit(TEXTURE, leftPos + 124, topPos + 34, 176, 7 * 18, 18, 18, 256, 256);
        }
    }
}
