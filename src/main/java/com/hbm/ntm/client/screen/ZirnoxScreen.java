package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.ZirnoxBlockEntity;
import com.hbm.ntm.inventory.ZirnoxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

/** Original 203x256 ZIRNOX panel and controls. */
public final class ZirnoxScreen extends AbstractContainerScreen<ZirnoxMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/reactors/gui_zirnox.png");

    public ZirnoxScreen(ZirnoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 203; imageHeight = 256; inventoryLabelX = 8; inventoryLabelY = 160;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(160, 108, 18, 12, mouseX, mouseY)) tankTip(graphics, "Superheated Steam", menu.steam(),
                ZirnoxBlockEntity.STEAM_CAPACITY, mouseX, mouseY);
        else if (isHovering(142, 108, 18, 12, mouseX, mouseY)) tankTip(graphics, "Carbon Dioxide", menu.carbonDioxide(),
                ZirnoxBlockEntity.CO2_CAPACITY, mouseX, mouseY);
        else if (isHovering(178, 108, 18, 12, mouseX, mouseY)) tankTip(graphics, "Water", menu.water(),
                ZirnoxBlockEntity.WATER_CAPACITY, mouseX, mouseY);
        else if (isHovering(160, 33, 18, 17, mouseX, mouseY)) graphics.renderTooltip(font,
                List.of(Component.literal("Temperature:"), Component.literal("   " + Math.round(menu.heat() * .00001D * 780D + 20D) + "°C")),
                Optional.empty(), mouseX, mouseY);
        else if (isHovering(178, 33, 18, 17, mouseX, mouseY)) graphics.renderTooltip(font,
                List.of(Component.literal("Pressure:"), Component.literal("   " + Math.round(menu.pressure() * .00001D * 30D) + " bar")),
                Optional.empty(), mouseX, mouseY);
        else if (isHovering(144, 35, 14, 14, mouseX, mouseY) && menu.redstoneLocked()) graphics.renderTooltip(font,
                List.of(Component.literal("Locked by redstone")), Optional.empty(), mouseX, mouseY);
        else if (isHovering(151, 51, 36, 36, mouseX, mouseY)) graphics.renderTooltip(font,
                List.of(Component.literal("Vent 1,000 mB CO₂")), Optional.empty(), mouseX, mouseY);
    }

    private void tankTip(GuiGraphics graphics, String name, int amount, int capacity, int x, int y) {
        graphics.renderTooltip(font, List.of(Component.literal(name), Component.literal(amount + "/" + capacity + "mB")),
                Optional.empty(), x, y);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        gauge(graphics, menu.steam(), ZirnoxBlockEntity.STEAM_CAPACITY, 160, 108, 12, 12, 6, 238);
        gauge(graphics, menu.carbonDioxide(), ZirnoxBlockEntity.CO2_CAPACITY, 142, 108, 12, 12, 6, 238);
        gauge(graphics, menu.water(), ZirnoxBlockEntity.WATER_CAPACITY, 178, 108, 12, 12, 6, 238);
        gauge(graphics, menu.heat(), ZirnoxBlockEntity.MAX_HEAT, 160, 33, 17, 18, 12, 220);
        gauge(graphics, menu.pressure(), ZirnoxBlockEntity.MAX_PRESSURE, 178, 33, 17, 18, 12, 220);
        if (menu.isOn()) {
            for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++)
                graphics.blit(TEXTURE, leftPos + 7 + 36 * x, topPos + 15 + 36 * y, 238, 238, 18, 18, 256, 256);
            for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++)
                graphics.blit(TEXTURE, leftPos + 25 + 36 * x, topPos + 33 + 36 * y, 238, 238, 18, 18, 256, 256);
            graphics.blit(TEXTURE, leftPos + 142, topPos + 15, 220, 238, 18, 18, 256, 256);
        }
    }

    private void gauge(GuiGraphics graphics, int value, int max, int x, int y,
                       int spriteHeight, int frameStep, int frames, int u) {
        int frame = Math.min(Math.max(value, 0) * frames / Math.max(max, 1), frames - 1);
        graphics.blit(TEXTURE, leftPos + x, topPos + y, u, frameStep * frame, 18, spriteHeight, 256, 256);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (minecraft != null && minecraft.gameMode != null && button == 0) {
            int id = -1;
            if (isHovering(144, 35, 14, 14, mouseX, mouseY)) id = 0;
            else if (isHovering(151, 51, 36, 36, mouseX, mouseY)) id = 1;
            if (id >= 0) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
