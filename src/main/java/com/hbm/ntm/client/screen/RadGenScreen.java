package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.RadGenBlockEntity;
import com.hbm.ntm.inventory.RadGenMenu;
import com.hbm.ntm.item.BatteryPackItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

public final class RadGenScreen extends AbstractContainerScreen<RadGenMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/reactors/gui_radgen.png");

    public RadGenScreen(RadGenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 184;
        inventoryLabelX = 8;
        inventoryLabelY = 90;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(64, 83, 48, 4, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(
                    BatteryPackItem.shortNumber(menu.power()) + "/"
                            + BatteryPackItem.shortNumber(RadGenBlockEntity.MAX_POWER) + "HE")),
                    Optional.empty(), mouseX, mouseY);
        }
        for (int lane = 0; lane < RadGenBlockEntity.LANE_COUNT; lane++) {
            int maximum = menu.maxProgress(lane);
            if (maximum <= 0 || !isHovering(65, 18 + lane * 5, 46, 5, mouseX, mouseY)) continue;
            int remaining = maximum - menu.progress(lane);
            graphics.renderTooltip(font, List.of(
                    Component.literal("Slot " + (lane + 1) + ":"),
                    Component.literal(menu.production(lane) + "HE/t for"),
                    Component.literal(remaining + " ticks (" + remaining * 100 / maximum + "%)")),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        for (int lane = 0; lane < RadGenBlockEntity.LANE_COUNT; lane++) {
            int maximum = menu.maxProgress(lane);
            if (maximum <= 0) continue;
            int width = menu.progress(lane) * 44 / maximum;
            graphics.blit(TEXTURE, leftPos + 66, topPos + 19 + lane * 5,
                    176, 0, width, 3, 256, 256);
        }
        int powerWidth = (int) (menu.power() * 48L / RadGenBlockEntity.MAX_POWER);
        graphics.blit(TEXTURE, leftPos + 64, topPos + 83,
                176, 3, powerWidth, 4, 256, 256);
    }
}
