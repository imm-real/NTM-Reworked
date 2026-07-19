package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.SolderingStationMenu;
import com.hbm.ntm.network.SolderingCollisionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

public final class SolderingStationScreen extends AbstractContainerScreen<SolderingStationMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_soldering_station.png");

    public SolderingStationScreen(SolderingStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 204;
        inventoryLabelX = 8;
        inventoryLabelY = 110;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(152, 18, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal(menu.power() + "/" + menu.maxPower() + " HE")),
                    Optional.empty(), mouseX, mouseY);
        }
        if (isHovering(35, 63, 34, 16, mouseX, mouseY) && menu.blockEntity() != null) {
            var tank = menu.blockEntity().tank();
            graphics.renderTooltip(font, List.of(Component.literal(tank.getFluidAmount() + "/8000 mB")),
                    Optional.empty(), mouseX, mouseY);
        }
        if (isHovering(5, 66, 10, 10, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.literal("Recipe Collision Prevention: " + (menu.collisionPrevention() ? "ON" : "OFF")),
                    Component.literal("Prevents no-fluid recipes from being processed"),
                    Component.literal("when fluid is present.")), Optional.empty(), mouseX, mouseY);
        }
        if (isHovering(78, 67, 8, 8, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.literal("Speed: -16.7% delay, +100% consumption per level"),
                    Component.literal("Power: -16.7% consumption, +33.3% delay per level"),
                    Component.literal("Overdrive: doubled power and +1 progress/tick per level")),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        if (menu.collisionPrevention()) graphics.blit(TEXTURE, leftPos + 5, topPos + 66,
                192, 14, 10, 10, 256, 256);
        int powerHeight = (int) (menu.power() * 52L / Math.max(menu.maxPower(), 1L));
        if (powerHeight > 0) graphics.blit(TEXTURE, leftPos + 152, topPos + 70 - powerHeight,
                176, 52 - powerHeight, 16, powerHeight, 256, 256);
        int progressWidth = menu.progress() * 33 / menu.processTime();
        graphics.blit(TEXTURE, leftPos + 72, topPos + 28, 192, 0,
                progressWidth, 14, 256, 256);
        if (menu.power() >= menu.consumption()) graphics.blit(TEXTURE, leftPos + 156, topPos + 4,
                176, 52, 9, 12, 256, 256);
        if (menu.blockEntity() != null && !menu.blockEntity().tank().isEmpty()) {
            var fluid = menu.blockEntity().tank().getFluid();
            int width = fluid.getAmount() * 34 / 8_000;
            int color = fluid.getFluid().isSame(net.minecraft.world.level.material.Fluids.LAVA)
                    ? 0xB0FF3300 : 0xB03333FF;
            graphics.fill(leftPos + 35, topPos + 79, leftPos + 35 + width, topPos + 95, color);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2 - 18, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= leftPos + 5 && mouseX < leftPos + 15 && mouseY > topPos + 66 && mouseY <= topPos + 76) {
            PacketDistributor.sendToServer(new SolderingCollisionPayload());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
