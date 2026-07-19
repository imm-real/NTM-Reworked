package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.ArcWelderBlockEntity;
import com.hbm.ntm.inventory.ArcWelderMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

public final class ArcWelderScreen extends AbstractContainerScreen<ArcWelderMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_arc_welder.png");

    public ArcWelderScreen(ArcWelderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 204;
        inventoryLabelX = 8;
        inventoryLabelY = 110;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(152, 18, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(menu.power() + "/" + menu.maxPower() + " HE")),
                    Optional.empty(), mouseX, mouseY);
        }
        if (isHovering(35, 63, 34, 16, mouseX, mouseY) && menu.blockEntity() != null) {
            var tank = menu.blockEntity().tank();
            FluidIdentifierItem.Selection selection = menu.blockEntity().configuredFluid();
            Component name = selection == FluidIdentifierItem.Selection.NONE
                    ? tank.getFluid().getHoverName() : Component.translatable(selection.translationKey());
            graphics.renderTooltip(font, List.of(name, Component.literal(
                            tank.getFluidAmount() + "/" + ArcWelderBlockEntity.TANK_CAPACITY + "mB")),
                    Optional.empty(), mouseX, mouseY);
        }
        if (isHovering(78, 67, 8, 8, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal("Speed: -16.7% delay, +100% consumption per level"),
                    Component.literal("Power: -16.7% consumption, +33.3% delay per level"),
                    Component.literal("Overdrive: doubled power and +1 progress/tick per level")),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int powerHeight = (int) (menu.power() * 52L / Math.max(menu.maxPower(), 1L));
        if (powerHeight > 0) graphics.blit(TEXTURE, leftPos + 152, topPos + 70 - powerHeight,
                176, 52 - powerHeight, 16, powerHeight, 256, 256);
        int progressWidth = menu.progress() * 33 / menu.processTime();
        if (progressWidth > 0) graphics.blit(TEXTURE, leftPos + 72, topPos + 37,
                192, 0, progressWidth, 14, 256, 256);
        if (menu.power() >= menu.consumption()) graphics.blit(TEXTURE, leftPos + 156, topPos + 4,
                176, 52, 9, 12, 256, 256);
        if (menu.blockEntity() != null && !menu.blockEntity().tank().isEmpty()) {
            var fluid = menu.blockEntity().tank().getFluid();
            int width = fluid.getAmount() * 34 / ArcWelderBlockEntity.TANK_CAPACITY;
            FluidIdentifierItem.Selection selection = selectionFor(fluid);
            if (width > 0 && selection != FluidIdentifierItem.Selection.NONE) {
                ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                        "textures/gui/fluids/" + selection.id() + ".png");
                // Source orientation 1 fills left-to-right while sampling U from 1
                // backwards, repeating the 16px fluid texture across the bar.
                graphics.blit(texture, leftPos + 35, topPos + 63,
                        width, 16, 16F, 0F, -width, 16, 16, 16);
            }
        }
    }

    private static FluidIdentifierItem.Selection selectionFor(
            net.neoforged.neoforge.fluids.FluidStack fluid) {
        if (fluid.isEmpty()) return FluidIdentifierItem.Selection.NONE;
        for (FluidIdentifierItem.Selection selection : FluidIdentifierItem.Selection.values()) {
            if (selection.accepts(fluid.getFluid())) return selection;
        }
        return FluidIdentifierItem.Selection.NONE;
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2 - 18, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
