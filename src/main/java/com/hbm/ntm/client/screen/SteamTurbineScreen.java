package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.SteamTurbineMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

/** Layout dictated by gui_turbine.png and centuries of turbine tradition. */
public final class SteamTurbineScreen extends AbstractContainerScreen<SteamTurbineMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/gui_turbine.png");
    private static final ResourceLocation UTILITY = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/gui_utility.png");

    public SteamTurbineScreen(SteamTurbineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 168;
        inventoryLabelX = 8;
        inventoryLabelY = 74;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(62, 17, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, tankTooltip(menu.inputSelection(), menu.inputAmount(), menu.inputCapacity()),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(134, 17, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, tankTooltip(menu.outputSelection(), menu.outputAmount(), menu.outputCapacity()),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(123, 35, 7, 34, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(menu.power() + "/" + menu.maxPower() + " HE")),
                    Optional.empty(), mouseX, mouseY);
        } else if (menu.outputSelection() == FluidIdentifierItem.Selection.NONE
                && mouseX >= leftPos - 16 && mouseX < leftPos
                && mouseY >= topPos + 68 && mouseY < topPos + 84) {
            graphics.renderTooltip(font, List.of(Component.literal("Error: Invalid fluid!")),
                    Optional.empty(), leftPos - 8, topPos + 84);
        }
    }

    private static List<Component> tankTooltip(FluidIdentifierItem.Selection selection, int amount, int capacity) {
        return List.of(Component.translatable(selection.translationKey()),
                Component.literal(amount + "/" + capacity + "mB"));
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int grade = switch (menu.inputSelection()) {
            case STEAM -> 0;
            case HOTSTEAM -> 1;
            case SUPERHOTSTEAM -> 2;
            case ULTRAHOTSTEAM -> 3;
            default -> -1;
        };
        if (grade >= 0) graphics.blit(TEXTURE, leftPos + 99, topPos + 18,
                183, grade * 14, 14, 14, 256, 256);

        int powerHeight = (int) (menu.power() * 34L / Math.max(menu.maxPower(), 1L));
        if (powerHeight > 0) graphics.blit(TEXTURE, leftPos + 123, topPos + 69 - powerHeight,
                176, 34 - powerHeight, 7, powerHeight, 256, 256);

        drawFluid(graphics, menu.inputSelection(), menu.inputAmount(), menu.inputCapacity(), 62);
        drawFluid(graphics, menu.outputSelection(), menu.outputAmount(), menu.outputCapacity(), 134);
        if (menu.outputSelection() == FluidIdentifierItem.Selection.NONE) {
            graphics.blit(UTILITY, leftPos - 16, topPos + 68, 8, 16, 16, 16, 256, 256);
        }
    }

    private void drawFluid(GuiGraphics graphics, FluidIdentifierItem.Selection selection,
                           int amount, int capacity, int x) {
        if (selection == FluidIdentifierItem.Selection.NONE || amount <= 0 || capacity <= 0) return;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "textures/gui/fluids/" + selection.id() + ".png");
        int remaining = amount * 52 / capacity;
        int bottom = topPos + 69;
        while (remaining > 0) {
            int strip = Math.min(16, remaining);
            bottom -= strip;
            graphics.blit(texture, leftPos + x, bottom, 0, 16 - strip, 16, strip, 16, 16);
            remaining -= strip;
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
