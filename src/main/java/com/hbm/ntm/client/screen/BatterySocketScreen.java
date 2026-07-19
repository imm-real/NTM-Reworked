package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.BatterySocketMenu;
import com.hbm.ntm.item.BatteryPackItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BatterySocketScreen extends AbstractContainerScreen<BatterySocketMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/storage/gui_battery_socket.png");

    public BatterySocketScreen(BatterySocketMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 181;
        inventoryLabelX = 8;
        inventoryLabelY = 87;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(62, 17, 34, 52, mouseX, mouseY) && menu.maxPower() > 0L) {
            String delta = BatteryPackItem.shortNumber(Math.abs(menu.delta())) + "HE/s";
            if (menu.delta() > 0L) delta = ChatFormatting.GREEN + "+" + delta;
            else if (menu.delta() < 0L) delta = ChatFormatting.RED + "-" + delta;
            else delta = ChatFormatting.YELLOW + "+" + delta;
            graphics.renderTooltip(font, List.of(
                    Component.literal(BatteryPackItem.shortNumber(menu.power()) + "/"
                            + BatteryPackItem.shortNumber(menu.maxPower()) + "HE"),
                    Component.literal(delta)
            ), Optional.empty(), mouseX, mouseY);
        }
        if (isHovering(125, 35, 16, 16, mouseX, mouseY)) {
            String key = switch (menu.priority()) {
                case 3 -> "high";
                case 2 -> "normal";
                default -> "low";
            };
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("battery.priority." + key));
            lines.add(Component.translatable("battery.priority.recommended"));
            for (int i = 0; i < 3; i++) {
                Component line = Component.translatable("battery.priority." + key + ".desc." + i);
                if (!line.getString().equals("battery.priority." + key + ".desc." + i)) lines.add(line);
            }
            graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        long max = Math.max(menu.maxPower(), 1L);
        long power = menu.power();
        if (power > Long.MAX_VALUE / 100L) {
            power /= 100L;
            max /= 100L;
        }
        int height = (int) (power * 52L / max);
        if (height > 0) {
            graphics.blit(TEXTURE, leftPos + 62, topPos + 69 - height,
                    176, 52 - height, 34, height, 256, 256);
        }
        graphics.blit(TEXTURE, leftPos + 106, topPos + 16,
                176, 52 + menu.lowMode() * 18, 18, 18, 256, 256);
        graphics.blit(TEXTURE, leftPos + 106, topPos + 52,
                176, 52 + menu.highMode() * 18, 18, 18, 256, 256);
        int priority = Math.max(1, Math.min(menu.priority(), 3));
        graphics.blit(TEXTURE, leftPos + 125, topPos + 35,
                194, 52 + (priority - 1) * 16, 16, 16, 256, 256);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            int relativeX = (int) mouseX - leftPos;
            int relativeY = (int) mouseY - topPos;
            int control = -1;
            if (inside(relativeX, relativeY, 106, 16, 18, 18)) control = 0;
            else if (inside(relativeX, relativeY, 106, 52, 18, 18)) control = 1;
            else if (inside(relativeX, relativeY, 125, 35, 16, 16)) control = 2;
            if (control >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, control);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
