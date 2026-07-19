package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.DieselGeneratorBlockEntity;
import com.hbm.ntm.inventory.DieselGeneratorMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

public final class DieselGeneratorScreen extends AbstractContainerScreen<DieselGeneratorMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_diesel.png");
    private static final ResourceLocation UTILITY = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/gui_utility.png");

    public DieselGeneratorScreen(DieselGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelX = 8;
        inventoryLabelY = 72;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(80, 17, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable(menu.selectedFluid().translationKey()),
                    Component.literal(menu.fuelAmount() + "/" + DieselGeneratorBlockEntity.FUEL_CAPACITY + "mB")),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(152, 17, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(menu.power() + "/" + menu.maxPower() + " HE")),
                    Optional.empty(), mouseX, mouseY);
        } else if (mouseX >= leftPos - 16 && mouseX < leftPos && mouseY >= topPos + 36 && mouseY < topPos + 52) {
            graphics.renderTooltip(font, List.of(
                    Component.literal("Fuel consumption rate:"),
                    Component.literal("  1 mB/t"),
                    Component.literal("  20 mB/s"),
                    Component.literal("(Consumption rate is constant)")), Optional.empty(), leftPos - 8, topPos + 52);
        } else if (!menu.acceptableFuel() && mouseX >= leftPos - 16 && mouseX < leftPos
                && mouseY >= topPos + 68 && mouseY < topPos + 84) {
            graphics.renderTooltip(font, List.of(
                    Component.literal("Error: The currently set fuel type"),
                    Component.literal("is not supported by this engine!")), Optional.empty(), leftPos - 8, topPos + 84);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int powerHeight = (int) (menu.power() * 52L / Math.max(menu.maxPower(), 1L));
        if (powerHeight > 0) graphics.blit(TEXTURE, leftPos + 152, topPos + 69 - powerHeight,
                176, 52 - powerHeight, 16, powerHeight, 256, 256);
        if (menu.fuelAmount() > 0 && menu.acceptableFuel()) {
            graphics.blit(TEXTURE, leftPos + 115, topPos + 34, 208, 0, 18, 18, 256, 256);
        }
        drawFluid(graphics, menu.selectedFluid(), menu.fuelAmount());
        graphics.blit(UTILITY, leftPos - 16, topPos + 36, 8, 0, 16, 16, 256, 256);
        if (!menu.acceptableFuel()) graphics.blit(UTILITY, leftPos - 16, topPos + 68,
                8, 16, 16, 16, 256, 256);
    }

    private void drawFluid(GuiGraphics graphics, FluidIdentifierItem.Selection selection, int amount) {
        if (selection == FluidIdentifierItem.Selection.NONE || amount <= 0) return;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "textures/gui/fluids/" + selection.id() + ".png");
        int remaining = amount * 52 / DieselGeneratorBlockEntity.FUEL_CAPACITY;
        int bottom = topPos + 69;
        while (remaining > 0) {
            int strip = Math.min(16, remaining);
            bottom -= strip;
            graphics.blit(texture, leftPos + 80, bottom, 0, 16 - strip, 16, strip, 16, 16);
            remaining -= strip;
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
