package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.WoodBurnerBlockEntity;
import com.hbm.ntm.inventory.WoodBurnerMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

/** Wood burner panel, arranged by gui_wood_burner_alt.png decree. */
public final class WoodBurnerScreen extends AbstractContainerScreen<WoodBurnerMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/generators/gui_wood_burner_alt.png");

    public WoodBurnerScreen(WoodBurnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelX = 8;
        inventoryLabelY = 92;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(143, 18, 16, 34, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(menu.power() + "/"
                    + WoodBurnerBlockEntity.MAX_POWER + " HE")), Optional.empty(), mouseX, mouseY);
        } else if (menu.liquidBurn() && isHovering(80, 18, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.translatable(menu.selectedFluid().translationKey()),
                    Component.literal(menu.tankAmount() + "/" + WoodBurnerBlockEntity.TANK_CAPACITY + "mB")),
                    Optional.empty(), mouseX, mouseY);
        } else if (!menu.liquidBurn() && isHovering(16, 17, 8, 54, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(menu.burnTime() / 20 + "s")),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(53, 17, 16, 15, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(menu.isOn() ? "ON" : "OFF")
                    .withStyle(menu.isOn() ? ChatFormatting.GREEN : ChatFormatting.RED)),
                    Optional.empty(), mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index == WoodBurnerBlockEntity.SOLID_FUEL
                && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            graphics.renderTooltip(font, List.of(
                    Component.literal("Burn time bonuses:").withStyle(ChatFormatting.GOLD),
                    Component.literal("- Logs: ").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("+300%").withStyle(ChatFormatting.GREEN)),
                    Component.literal("- Wood: ").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("+100%").withStyle(ChatFormatting.GREEN))),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= leftPos + 53 && mouseX < leftPos + 69
                && mouseY > topPos + 17 && mouseY <= topPos + 32) {
            sendButton(0);
            return true;
        }
        if (mouseX >= leftPos + 46 && mouseX < leftPos + 76
                && mouseY > topPos + 37 && mouseY <= topPos + 51) {
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
        if (menu.liquidBurn()) {
            graphics.blit(TEXTURE, leftPos + 16, topPos + 17, 176, 52, 60, 54, 256, 256);
            graphics.blit(TEXTURE, leftPos + 79, topPos + 17, 176, 106, 36, 54, 256, 256);
            drawFluid(graphics, menu.selectedFluid(), menu.tankAmount());
        }
        if (menu.isOn()) {
            graphics.blit(TEXTURE, leftPos + 53, topPos + 17, 196, 0, 16, 15, 256, 256);
        }
        int powerHeight = (int) (menu.power() * 34L / WoodBurnerBlockEntity.MAX_POWER);
        if (powerHeight > 0) {
            graphics.blit(TEXTURE, leftPos + 143, topPos + 52 - powerHeight,
                    176, 52 - powerHeight, 16, powerHeight, 256, 256);
        }
        if (!menu.liquidBurn() && menu.maxBurnTime() > 0) {
            int burnHeight = menu.burnTime() * 52 / menu.maxBurnTime();
            if (burnHeight > 0) graphics.blit(TEXTURE, leftPos + 17, topPos + 70 - burnHeight,
                    192, 52 - burnHeight, 4, burnHeight, 256, 256);
        }
    }

    private void drawFluid(GuiGraphics graphics, FluidIdentifierItem.Selection selection, int amount) {
        if (selection == FluidIdentifierItem.Selection.NONE || amount <= 0) return;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "textures/gui/fluids/" + selection.id() + ".png");
        int remaining = amount * 52 / WoodBurnerBlockEntity.TANK_CAPACITY;
        int bottom = topPos + 70;
        while (remaining > 0) {
            int strip = Math.min(16, remaining);
            bottom -= strip;
            graphics.blit(texture, leftPos + 80, bottom, 0, 16 - strip, 16, strip, 16, 16);
            remaining -= strip;
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 70 - font.width(title) / 2, 6, 0xFFFFFF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
