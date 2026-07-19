package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.CraneExtractorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public final class CraneExtractorScreen extends AbstractContainerScreen<CraneExtractorMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/storage/gui_crane_ejector.png");

    public CraneExtractorScreen(CraneExtractorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 212;
        imageHeight = 185;
        inventoryLabelX = 26;
        inventoryLabelY = 91;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(187, 34, 18, 18, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal("Only take maximum possible: ")
                    .append(Component.literal(menu.maxEject() ? "ON" : "OFF")
                            .withStyle(menu.maxEject() ? ChatFormatting.GREEN : ChatFormatting.RED)), mouseX, mouseY);
        }
        for (int slot = 0; slot < 9; slot++) {
            if (menu.getSlot(slot).hasItem() && isHovering(menu.getSlot(slot).x, menu.getSlot(slot).y,
                    16, 16, mouseX, mouseY)) {
                graphics.renderTooltip(font, List.of(
                        Component.literal("Right click to change").withStyle(ChatFormatting.RED).getVisualOrderText(),
                        Component.literal(menu.exactFilter(slot) ? "Item and components match" : "Item matches")
                                .withStyle(ChatFormatting.YELLOW).getVisualOrderText()), mouseX, mouseY);
            }
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        if (menu.maxEject()) graphics.blit(TEXTURE, leftPos + 187, topPos + 34, 212, 0,
                18, 18, 256, 256);
        graphics.blit(TEXTURE, leftPos + 139, topPos + (menu.whitelist() ? 33 : 47), 212, 18,
                3, 6, 256, 256);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= leftPos + 187 && mouseX < leftPos + 205
                && mouseY > topPos + 34 && mouseY <= topPos + 52) {
            clickButton(1);
            return true;
        }
        if (button == 0 && mouseX >= leftPos + 128 && mouseX < leftPos + 142
                && mouseY > topPos + 30 && mouseY <= topPos + 56) {
            clickButton(0);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void clickButton(int id) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
}
