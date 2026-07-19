package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.NukeManMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public final class NukeManScreen extends AbstractContainerScreen<NukeManMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/weapon/fat_man_schematic.png");
    private static final ResourceLocation GUI_UTILITY = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/gui_utility.png");

    public NukeManScreen(NukeManMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelX = 8;
        inventoryLabelY = 72;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int infoX = leftPos - 16;
        int infoY = topPos + 16;
        if (mouseX >= infoX && mouseX < infoX + 16 && mouseY >= infoY && mouseY < infoY + 16) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("desc.gui.nukeMan.desc.0"),
                    Component.translatable("desc.gui.nukeMan.desc.1"),
                    Component.translatable("desc.gui.nukeMan.desc.2"),
                    Component.translatable("desc.gui.nukeMan.desc.3"),
                    Component.translatable("desc.gui.nukeMan.desc.4")
            ), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        if (menu.hasLens(1)) graphics.blit(TEXTURE, leftPos + 82, topPos + 19, 176, 0, 24, 24, 256, 256);
        if (menu.hasLens(2)) graphics.blit(TEXTURE, leftPos + 106, topPos + 19, 200, 0, 24, 24, 256, 256);
        if (menu.hasLens(3)) graphics.blit(TEXTURE, leftPos + 82, topPos + 43, 176, 24, 24, 24, 256, 256);
        if (menu.hasLens(4)) graphics.blit(TEXTURE, leftPos + 106, topPos + 43, 200, 24, 24, 24, 256, 256);
        if (menu.isReady()) graphics.blit(TEXTURE, leftPos + 134, topPos + 35, 176, 48, 16, 16, 256, 256);
        graphics.blit(GUI_UTILITY, leftPos - 16, topPos + 16, 8, 0, 16, 16, 256, 256);
    }
}
