package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.WasteDrumMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

public final class WasteDrumScreen extends AbstractContainerScreen<WasteDrumMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/gui_waste_drum.png");
    private static final ResourceLocation UTILITY = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/gui_utility.png");

    public WasteDrumScreen(WasteDrumMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 189;
        inventoryLabelX = 8;
        inventoryLabelY = 98;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos - 16 && mouseX < leftPos
                && mouseY >= topPos + 36 && mouseY < topPos + 52) {
            graphics.renderTooltip(font, List.of(
                    Component.literal("The drum will cool down hot nuclear"),
                    Component.literal("waste when submerged in water. More"),
                    Component.literal("water speeds up the process.")),
                    Optional.empty(), leftPos - 8, topPos + 52);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        // GuiInfoContainer type 2: large blue information panel.
        graphics.blit(UTILITY, leftPos - 16, topPos + 36, 8, 0, 16, 16, 256, 256);
    }
}
