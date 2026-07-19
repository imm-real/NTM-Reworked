package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.CombinationOvenBlockEntity;
import com.hbm.ntm.inventory.CombinationOvenMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Combination oven panel, all 176x186 pixels accounted for. */
public final class CombinationOvenScreen extends AbstractContainerScreen<CombinationOvenMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_furnace_combination.png");
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    public CombinationOvenScreen(CombinationOvenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelX = 8;
        inventoryLabelY = 92;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(44, 36, 39, 7, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal(FORMAT.format(menu.progress()) + " / "
                    + FORMAT.format(CombinationOvenBlockEntity.PROCESS_TIME) + "TU"), mouseX, mouseY);
        } else if (isHovering(44, 45, 39, 7, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal(FORMAT.format(menu.heat()) + " / "
                    + FORMAT.format(CombinationOvenBlockEntity.MAX_HEAT) + "TU"), mouseX, mouseY);
        } else if (isHovering(118, 18, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                            Component.translatable(menu.fluidSelection().translationKey()),
                            Component.literal(menu.fluidAmount() + "/"
                                    + CombinationOvenBlockEntity.TANK_CAPACITY + "mB")),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int progress = menu.progress() * 38 / CombinationOvenBlockEntity.PROCESS_TIME;
        if (progress > 0) graphics.blit(TEXTURE, leftPos + 45, topPos + 37,
                176, 0, progress, 5, 256, 256);
        int heat = menu.heat() * 37 / CombinationOvenBlockEntity.MAX_HEAT;
        if (heat > 0) graphics.blit(TEXTURE, leftPos + 45, topPos + 46,
                176, 5, heat, 5, 256, 256);
        drawFluid(graphics, menu.fluidSelection(), menu.fluidAmount(), leftPos + 118, topPos + 18);
    }

    private static void drawFluid(GuiGraphics graphics, FluidIdentifierItem.Selection selection,
                                  int amount, int x, int y) {
        if (selection == FluidIdentifierItem.Selection.NONE || amount <= 0) return;
        ResourceLocation fluid = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "textures/gui/fluids/" + selection.id() + ".png");
        int height = Math.clamp(amount * 52 / CombinationOvenBlockEntity.TANK_CAPACITY, 1, 52);
        int bottom = y + 52;
        while (height > 0) {
            int strip = Math.min(16, height);
            bottom -= strip;
            graphics.blit(fluid, x, bottom, 0, 16 - strip, 16, strip, 16, 16);
            height -= strip;
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
