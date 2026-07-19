package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.OilDerrickBlockEntity;
import com.hbm.ntm.inventory.OilDerrickMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

public final class OilDerrickScreen extends AbstractContainerScreen<OilDerrickMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/machine/gui_well.png");

    public OilDerrickScreen(OilDerrickMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelX = 8;
        inventoryLabelY = 72;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(8, 17, 16, 34, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(menu.power() + "/" + menu.maxPower() + " HE")),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(62, 17, 16, 52, mouseX, mouseY)) {
            tankTooltip(graphics, Component.translatable("hbmfluid.oil"), menu.oil(), mouseX, mouseY);
        } else if (isHovering(107, 17, 16, 52, mouseX, mouseY)) {
            tankTooltip(graphics, Component.translatable("hbmfluid.gas"), menu.gas(), mouseX, mouseY);
        } else if (isHovering(156, 3, 8, 8, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable("desc.gui.upgrade"),
                    Component.translatable("desc.gui.upgrade.speed"),
                    Component.translatable("desc.gui.upgrade.power"),
                    Component.translatable("desc.gui.upgrade.afterburner")), Optional.empty(), mouseX, mouseY);
        }
    }

    private void tankTooltip(GuiGraphics graphics, Component fluid, int amount, int mouseX, int mouseY) {
        graphics.renderTooltip(font, List.of(fluid,
                Component.literal(amount + "/" + OilDerrickBlockEntity.TANK_CAPACITY + " mB")),
                Optional.empty(), mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int powerHeight = (int) Math.min(34L, menu.power() * 34L / Math.max(menu.maxPower(), 1L));
        if (powerHeight > 0) graphics.blit(TEXTURE, leftPos + 8, topPos + 51 - powerHeight,
                176, 34 - powerHeight, 16, powerHeight, 256, 256);
        int indicator = menu.indicator();
        if (indicator != 0) graphics.blit(TEXTURE, leftPos + 35, topPos + 17,
                176 + (indicator - 1) * 16, 52, 16, 16, 256, 256);
        graphics.blit(TEXTURE, leftPos + 34, topPos + 36, 192, 0, 18, 34, 256, 256);
        drawTank(graphics, menu.oil(), 0xD0020202, leftPos + 62, topPos + 17);
        drawTank(graphics, menu.gas(), 0xD0FFFEED, leftPos + 107, topPos + 17);
    }

    private static void drawTank(GuiGraphics graphics, int amount, int color, int x, int y) {
        if (amount <= 0) return;
        int height = Math.max(1, amount * 52 / OilDerrickBlockEntity.TANK_CAPACITY);
        graphics.fill(x, y + 52 - height, x + 16, y + 52, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
