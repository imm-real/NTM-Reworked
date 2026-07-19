package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.MicrowaveBlockEntity;
import com.hbm.ntm.inventory.MicrowaveMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

/** Microwave panel with every pixel back in its assigned seat. */
public final class MicrowaveScreen extends AbstractContainerScreen<MicrowaveMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_microwave.png");

    public MicrowaveScreen(MicrowaveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 168;
        inventoryLabelX = 8;
        inventoryLabelY = 74;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(8, 17, 16, 34, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(menu.power() + "/"
                    + MicrowaveBlockEntity.MAX_POWER + " HE")), Optional.empty(), mouseX, mouseY);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= leftPos + 43 && mouseX < leftPos + 61
                && mouseY > topPos + 25 && mouseY <= topPos + 43) {
            sendButton(0);
            return true;
        }
        if (mouseX >= leftPos + 43 && mouseX < leftPos + 61
                && mouseY > topPos + 43 && mouseY <= topPos + 61) {
            sendButton(1);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int powerHeight = (int) (menu.power() * 34L / MicrowaveBlockEntity.MAX_POWER);
        if (powerHeight > 0) graphics.blit(TEXTURE, leftPos + 8, topPos + 51 - powerHeight,
                176, 34 - powerHeight, 16, powerHeight, 256, 256);
        int progressWidth = Math.min(menu.time() * 23 / MicrowaveBlockEntity.MAX_TIME, 22);
        if (progressWidth > 0) graphics.blit(TEXTURE, leftPos + 104, topPos + 34,
                192, 0, progressWidth, 16, 256, 256);
        int speedHeight = menu.speed() * 34 / MicrowaveBlockEntity.MAX_SPEED;
        if (speedHeight > 0) graphics.blit(TEXTURE, leftPos + 62, topPos + 60 - speedHeight,
                214, 34 - speedHeight, 4, speedHeight, 256, 256);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
