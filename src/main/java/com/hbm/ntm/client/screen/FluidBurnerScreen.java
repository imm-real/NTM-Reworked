package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.FluidBurnerBlockEntity;
import com.hbm.ntm.inventory.FluidBurnerMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.recipe.FluidBurnerFuels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class FluidBurnerScreen extends AbstractContainerScreen<FluidBurnerMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/machine/gui_oilburner.png");

    public FluidBurnerScreen(FluidBurnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 203;
        inventoryLabelX = 8;
        inventoryLabelY = 109;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
        if (isHovering(116, 17, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(format.format(Math.min(menu.heatEnergy(),
                    FluidBurnerBlockEntity.MAX_HEAT)) + " / "
                    + format.format(FluidBurnerBlockEntity.MAX_HEAT) + " TU")), Optional.empty(), mouseX, mouseY);
        } else if (isHovering(44, 17, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.translatable(menu.selectedFluid().translationKey()),
                    Component.literal(menu.fuelAmount() + "/" + FluidBurnerBlockEntity.FUEL_CAPACITY + "mB")),
                    Optional.empty(), mouseX, mouseY);
        } else if (FluidBurnerFuels.flammable(menu.selectedFluid())
                && isHovering(79, 34, 18, 18, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(menu.setting() + " mB/t"),
                    Component.literal(format.format(FluidBurnerFuels.heatPerMb(menu.selectedFluid())
                            * menu.setting()) + " TU/t")), Optional.empty(), mouseX, mouseY);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= leftPos + 80 && mouseX < leftPos + 96
                && mouseY > topPos + 54 && mouseY <= topPos + 68) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int heatHeight = menu.heatEnergy() * 52 / FluidBurnerBlockEntity.MAX_HEAT;
        if (heatHeight > 0) graphics.blit(TEXTURE, leftPos + 116, topPos + 69 - heatHeight,
                194, 52 - heatHeight, 16, heatHeight, 256, 256);
        if (menu.isOn()) {
            graphics.blit(TEXTURE, leftPos + 70, topPos + 54, 210, 0, 35, 14, 256, 256);
            if (menu.fuelAmount() > 0 && FluidBurnerFuels.flammable(menu.selectedFluid())) {
                graphics.blit(TEXTURE, leftPos + 79, topPos + 34, 176, 0, 18, 18, 256, 256);
            }
        }
        drawFluid(graphics, menu.selectedFluid(), menu.fuelAmount());
    }

    private void drawFluid(GuiGraphics graphics, FluidIdentifierItem.Selection selection, int amount) {
        if (selection == FluidIdentifierItem.Selection.NONE || amount <= 0) return;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "textures/gui/fluids/" + selection.id() + ".png");
        int remaining = amount * 52 / FluidBurnerBlockEntity.FUEL_CAPACITY;
        int bottom = topPos + 69;
        while (remaining > 0) {
            int strip = Math.min(16, remaining);
            bottom -= strip;
            graphics.blit(texture, leftPos + 44, bottom, 0, 16 - strip, 16, strip, 16, 16);
            remaining -= strip;
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
