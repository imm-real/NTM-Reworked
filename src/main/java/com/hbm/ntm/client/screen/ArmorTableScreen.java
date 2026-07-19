package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.armor.ArmorModHandler;
import com.hbm.ntm.inventory.ArmorTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class ArmorTableScreen extends AbstractContainerScreen<ArmorTableMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/machine/gui_armor_modifier.png");
    private static final String[] SLOT_KEYS = {
            "armorMod.type.helmet", "armorMod.type.chestplate", "armorMod.type.leggings",
            "armorMod.type.boots", "armorMod.type.servo", "armorMod.type.cladding",
            "armorMod.type.insert", "armorMod.type.special", "armorMod.type.battery",
            "armorMod.insertHere"
    };

    public ArmorTableScreen(ArmorTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 198;
        imageHeight = 222;
        inventoryLabelX = 30;
        inventoryLabelY = 128;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu.getCarried().isEmpty()) {
            for (int slot = 0; slot <= ArmorModHandler.MOD_SLOTS; slot++) {
                if (slot < menu.slots.size() && !menu.getSlot(slot).hasItem()
                        && isHovering(menu.getSlot(slot).x, menu.getSlot(slot).y, 16, 16, mouseX, mouseY)) {
                    graphics.renderTooltip(font, Component.translatable(SLOT_KEYS[slot]), mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 110 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos + 22, topPos, 0, 0, 176, 222, 256, 256);
        graphics.blit(TEXTURE, leftPos, topPos + 31, 176, 96, 22, 100, 256, 256);
        if (!menu.armorStack().isEmpty()) graphics.blit(TEXTURE, leftPos + 63, topPos + 60, 176, 74, 22, 22, 256, 256);
        else if (System.currentTimeMillis() % 1000L < 500L)
            graphics.blit(TEXTURE, leftPos + 63, topPos + 60, 176, 52, 22, 22, 256, 256);
        for (int slot = 0; slot < ArmorModHandler.MOD_SLOTS; slot++) {
            if (!menu.modStack(slot).isEmpty()) {
                int x = menu.getSlot(slot).x - 1;
                int y = menu.getSlot(slot).y - 1;
                graphics.blit(TEXTURE, leftPos + x, topPos + y, 176, 34, 18, 18, 256, 256);
            }
        }
    }
}
