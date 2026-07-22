package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.WeaponModifierMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;
import net.minecraft.world.entity.player.Inventory;

public final class WeaponModifierScreen extends AbstractContainerScreen<WeaponModifierMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/machine/gui_weapon_modifier.png");
    private double yaw = 20.0D;
    private double pitch = -10.0D;

    public WeaponModifierScreen(WeaponModifierMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 240;
        inventoryLabelX = 8;
        inventoryLabelY = 146;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 6, 0xFFFFFF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        if (!menu.gunStack().isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos + 88.0F, topPos + 57.0F, 100.0F);
            graphics.pose().mulPose(Axis.YP.rotationDegrees((float) yaw));
            graphics.pose().mulPose(Axis.XP.rotationDegrees((float) pitch));
            graphics.pose().scale(4.0F, 4.0F, 4.0F);
            graphics.renderItem(menu.gunStack(), -8, -8);
            graphics.pose().popPose();
        }
        graphics.blit(TEXTURE, leftPos + 35, topPos + 112,
                176 + menu.config() * 6, 0, 6, 8, 256, 256);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && mouseX >= leftPos + 8 && mouseX < leftPos + 168
                && mouseY > topPos + 18 && mouseY <= topPos + 97) {
            yaw = ((leftPos + 88.0D) - mouseX) / 80.0D * -180.0D;
            pitch = ((topPos + 57.5D) - mouseY) / 39.5D * 90.0D;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && menu.configCount() > 1
                && mouseX >= leftPos + 26 && mouseX < leftPos + 33
                && mouseY > topPos + 111 && mouseY <= topPos + 121) {
            int next = (menu.config() + 1) % menu.configCount();
            if (minecraft != null && minecraft.player != null && minecraft.gameMode != null) {
                menu.clickMenuButton(minecraft.player, next);
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, next);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
