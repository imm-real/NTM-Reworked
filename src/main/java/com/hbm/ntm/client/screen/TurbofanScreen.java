package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.TurbofanMenu;
import com.hbm.ntm.item.BatteryPackItem;
import com.hbm.ntm.item.FluidIdentifierItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

/** Turbofan panel with six afterburner frames and, regrettably, a blood gauge. */
public final class TurbofanScreen extends AbstractContainerScreen<TurbofanMenu> {
    private static final int FUEL_WIDTH = 34;
    private static final int FUEL_HEIGHT = 52;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/generators/gui_turbofan.png");
    private static final ResourceLocation SMALL_ROUND = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/gauges/small_round.png");

    public TurbofanScreen(TurbofanMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 203;
        inventoryLabelX = 8;
        inventoryLabelY = 109;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        if (isHovering(35, 17, FUEL_WIDTH, FUEL_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable(menu.selectedFluid().translationKey()),
                    Component.literal(menu.fuelAmount() + "/" + menu.fuelCapacity() + "mB")),
                    Optional.empty(), mouseX, mouseY);
        } else if (menu.showBlood() && isHovering(98, 17, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable(FluidIdentifierItem.Selection.BLOOD.translationKey()),
                    Component.literal(menu.bloodAmount() + "/" + menu.bloodCapacity() + "mB")),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(143, 17, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.literal(BatteryPackItem.shortNumber(menu.power()) + "/"
                            + BatteryPackItem.shortNumber(menu.maxPower()) + "HE")),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        int powerHeight = (int) (menu.power() * 52L / Math.max(menu.maxPower(), 1L));
        powerHeight = Mth.clamp(powerHeight, 0, 52);
        if (powerHeight > 0) {
            graphics.blit(TEXTURE, leftPos + 143, topPos + 69 - powerHeight,
                    192, 52 - powerHeight, 16, powerHeight, 256, 256);
        }

        if (menu.afterburner() > 0) {
            int frame = Math.min(menu.afterburner(), 6) - 1;
            graphics.blit(TEXTURE, leftPos + 98, topPos + 44,
                    176, frame * 16, 16, 16, 256, 256);
        }

        if (menu.showBlood()) {
            int frame = menu.bloodCapacity() <= 0 ? 0
                    : Mth.clamp((int) Math.round(12.0D * menu.bloodAmount() / menu.bloodCapacity()), 0, 12);
            graphics.blit(SMALL_ROUND, leftPos + 97, topPos + 16,
                    0, frame * 18, 18, 18, 18, 234);
        }

        drawFuel(graphics, menu.selectedFluid(), menu.fuelAmount(), menu.fuelCapacity());
    }

    private void drawFuel(GuiGraphics graphics, FluidIdentifierItem.Selection selection,
                          int amount, int capacity) {
        if (selection == FluidIdentifierItem.Selection.NONE || amount <= 0 || capacity <= 0) return;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "textures/gui/fluids/" + selection.id() + ".png");
        int fillHeight = Mth.clamp(amount * FUEL_HEIGHT / capacity, 0, FUEL_HEIGHT);
        int renderedHeight = 0;
        while (renderedHeight < fillHeight) {
            int strip = Math.min(16, fillHeight - renderedHeight);
            int y = topPos + 69 - renderedHeight - strip;
            for (int renderedWidth = 0; renderedWidth < FUEL_WIDTH; renderedWidth += 16) {
                int width = Math.min(16, FUEL_WIDTH - renderedWidth);
                graphics.blit(texture, leftPos + 35 + renderedWidth, y,
                        0, 16 - strip, width, strip, 16, 16);
            }
            renderedHeight += strip;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 43 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
