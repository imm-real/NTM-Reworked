package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.FireboxBlockEntity;
import com.hbm.ntm.inventory.FireboxMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class FireboxScreen extends AbstractContainerScreen<FireboxMenu> {
    private static final ResourceLocation FIREBOX_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/machine/gui_firebox.png");
    private static final ResourceLocation OVEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/machine/gui_heating_oven.png");
    private static final List<Component> FUEL_INFO = List.of(
            Component.literal("Burn time bonuses:").withStyle(ChatFormatting.GOLD),
            Component.literal("- Coal: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("+25%").withStyle(ChatFormatting.GREEN)),
            Component.literal("- Lignite: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("+25%").withStyle(ChatFormatting.GREEN)),
            Component.literal("- Coke: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("+25%").withStyle(ChatFormatting.GREEN)),
            Component.literal("- Solid Fuel: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("+50%").withStyle(ChatFormatting.GREEN)),
            Component.literal("- Rocket Fuel: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("+50%").withStyle(ChatFormatting.GREEN)),
            Component.literal("- Balefire: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("-50%").withStyle(ChatFormatting.RED)),
            Component.literal("Burn heat bonuses:").withStyle(ChatFormatting.RED),
            Component.literal("- Coal: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("+100%").withStyle(ChatFormatting.GREEN)),
            Component.literal("- Lignite: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("+100%").withStyle(ChatFormatting.GREEN)),
            Component.literal("- Coke: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("+100%").withStyle(ChatFormatting.GREEN)),
            Component.literal("- Solid Fuel: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("+200%").withStyle(ChatFormatting.GREEN)),
            Component.literal("- Rocket Fuel: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("+400%").withStyle(ChatFormatting.GREEN)),
            Component.literal("- Balefire: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("+1400%").withStyle(ChatFormatting.GREEN))
    );

    public FireboxScreen(FireboxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 168;
        inventoryLabelX = 8;
        inventoryLabelY = 74;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        if (getMenu().getCarried().isEmpty()) {
            for (int i = 0; i < 2; i++) {
                var slot = menu.getSlot(i);
                if (!slot.hasItem() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                    graphics.renderComponentTooltip(font, FUEL_INFO, mouseX, mouseY);
                    return;
                }
            }
        }
        if (isHovering(80, 27, 71, 7, mouseX, mouseY)) {
            NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
            graphics.renderTooltip(font, Component.literal(format.format(menu.heat()) + " / "
                    + format.format(menu.maxHeat()) + "TU"), mouseX, mouseY);
        } else if (isHovering(80, 36, 71, 7, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.literal(menu.burnHeat() + "TU/t"),
                    Component.literal((menu.burnTime() / 20) + "s")
            ), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6,
                menu.isHeatingOven() ? 0xFFFFFF : 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation texture = menu.isHeatingOven() ? OVEN_TEXTURE : FIREBOX_TEXTURE;
        graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int heatWidth = menu.heat() * 69 / menu.maxHeat();
        graphics.blit(texture, leftPos + 81, topPos + 28, 176, 0, heatWidth, 5, 256, 256);
        int burnWidth = menu.burnTime() * 70 / Math.max(menu.maxBurnTime(), 1);
        graphics.blit(texture, leftPos + 81, topPos + 37, 176, 5, burnWidth, 5, 256, 256);
        if (menu.wasOn()) {
            graphics.blit(texture, leftPos + 25, topPos + 26, 176, 10, 18, 18, 256, 256);
        }
    }
}
