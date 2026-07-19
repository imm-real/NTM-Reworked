package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.FensuMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class FensuScreen extends AbstractContainerScreen<FensuMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/storage/gui_battery_redd.png");

    public FensuScreen(FensuMenu menu, Inventory inventory, Component title) {
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
        if (isHovering(152, 35, 16, 16, mouseX, mouseY)) {
            String key = switch (menu.priority()) {
                case 3 -> "high";
                case 2 -> "normal";
                default -> "low";
            };
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("battery.priority." + key));
            lines.add(Component.translatable("battery.priority.recommended"));
            for (int index = 0; index < 3; index++) {
                Component line = Component.translatable("battery.priority." + key + ".desc." + index);
                if (!line.getString().equals("battery.priority." + key + ".desc." + index)) lines.add(line);
            }
            graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);

        graphics.pose().pushPose();
        graphics.pose().scale(0.5F, 0.5F, 1F);
        String power = String.format(Locale.US, "%,d", menu.power()) + " HE";
        graphics.drawString(font, power, 242 - font.width(power), 45, 0x00FF00, false);

        BigInteger deltaValue = menu.delta();
        String delta = String.format(Locale.US, "%,d", deltaValue) + " HE/s";
        int comparison = deltaValue.compareTo(BigInteger.ZERO);
        if (comparison > 0) delta = ChatFormatting.GREEN + "+" + delta;
        else if (comparison < 0) delta = ChatFormatting.RED + delta;
        else delta = ChatFormatting.YELLOW + "+" + delta;
        graphics.drawString(font, delta, 242 - font.width(delta), 65, 0x00FF00, false);
        graphics.pose().popPose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        graphics.blit(TEXTURE, leftPos + 133, topPos + 16,
                176, 52 + menu.lowMode() * 18, 18, 18, 256, 256);
        graphics.blit(TEXTURE, leftPos + 133, topPos + 52,
                176, 52 + menu.highMode() * 18, 18, 18, 256, 256);
        int priority = Math.max(1, Math.min(menu.priority(), 3));
        graphics.blit(TEXTURE, leftPos + 152, topPos + 35,
                194, 52 + (priority - 1) * 16, 16, 16, 256, 256);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            int relativeX = (int) mouseX - leftPos;
            int relativeY = (int) mouseY - topPos;
            int control = -1;
            if (inside(relativeX, relativeY, 133, 16, 18, 18)) control = 0;
            else if (inside(relativeX, relativeY, 133, 52, 18, 18)) control = 1;
            else if (inside(relativeX, relativeY, 152, 35, 16, 16)) control = 2;
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
