package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.BreedingReactorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

public final class BreedingReactorScreen extends AbstractContainerScreen<BreedingReactorMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_breeder.png");
    private static final ResourceLocation UTILITY = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/gui_utility.png");

    public BreedingReactorScreen(BreedingReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176; imageHeight = 166; inventoryLabelX = 8; inventoryLabelY = 72;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos - 16 && mouseX < leftPos && mouseY > topPos + 16 && mouseY <= topPos + 32) {
            graphics.renderTooltip(font, List.of(
                    Component.literal("The reactor has to recieve"),
                    Component.literal("neutron flux from adjacent"),
                    Component.literal("research reactors to breed.")), Optional.empty(), leftPos - 8, topPos + 32);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int width = (int) (menu.progress() * 70.0F);
        if (width > 0) graphics.blit(TEXTURE, leftPos + 53, topPos + 32, 176, 0, width, 20, 256, 256);
        graphics.blit(UTILITY, leftPos - 16, topPos + 16, 24, 0, 16, 16, 256, 256);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        String flux = Integer.toString(menu.flux());
        graphics.drawString(font, flux, 88 - font.width(flux) / 2, 21, 0x08FF00, false);
    }
}
