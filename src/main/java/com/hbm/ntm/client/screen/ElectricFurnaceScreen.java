package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.ElectricFurnaceBlockEntity;
import com.hbm.ntm.inventory.ElectricFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

/** Electric furnace panel with the gauges where the texture left them. */
public final class ElectricFurnaceScreen extends AbstractContainerScreen<ElectricFurnaceMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_electric_furnace.png");
    private static final ResourceLocation GUI_UTILITY = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/gui_utility.png");

    public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelX = 8;
        inventoryLabelY = 92;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(152, 18, 16, 34, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    List.of(Component.literal(menu.power() + "/" + ElectricFurnaceBlockEntity.MAX_POWER + " HE")),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(116, 20, 8, 8, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable("desc.gui.upgrade"),
                    Component.translatable("desc.gui.upgrade.speed"),
                    Component.translatable("desc.gui.upgrade.power")), Optional.empty(), mouseX, mouseY);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        if (menu.hasPower()) {
            int height = (int) (menu.power() * 34L / ElectricFurnaceBlockEntity.MAX_POWER);
            graphics.blit(TEXTURE, leftPos + 152, topPos + 52 - height,
                    176, 64 - height, 16, height, 256, 256);
        }

        if (menu.lit()) {
            graphics.blit(TEXTURE, leftPos + 45, topPos + 20, 192, 12, 18, 16, 256, 256);
            graphics.blit(TEXTURE, leftPos + 46, topPos + 47, 192, 28, 18, 16, 256, 256);
        }

        int width = menu.maxProgress() <= 0 ? 0 : menu.progress() * 28 / menu.maxProgress();
        graphics.blit(TEXTURE, leftPos + 43, topPos + 36, 176, 0, width, 12, 256, 256);

        // Type eight: the tiny blue upgrade asterisk.
        graphics.blit(GUI_UTILITY, leftPos + 116, topPos + 20, 0, 32, 8, 8, 256, 256);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
