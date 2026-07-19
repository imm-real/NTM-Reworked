package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.NukeN2BlockEntity;
import com.hbm.ntm.inventory.NukeN2Menu;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Twelve charges slowly color in the picture of your mistake. */
public final class NukeN2Screen extends AbstractContainerScreen<NukeN2Menu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/weapon/n2_schematic.png");

    public NukeN2Screen(NukeN2Menu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelX = 8;
        inventoryLabelY = imageHeight - 96 + 2;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int count = 0;
        for (int slot = 0; slot < NukeN2BlockEntity.SLOTS; slot++) {
            if (menu.getSlot(slot).getItem().is(ModItems.N2_CHARGE.get())) count++;
        }
        if (count > 0) {
            graphics.blit(TEXTURE, leftPos + 35, topPos + 120 - 6 * count, 176, 0, 34, 6 * count, 256, 256);
        }
    }
}
