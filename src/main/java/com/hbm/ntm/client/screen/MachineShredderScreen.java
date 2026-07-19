package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.MachineShredderBlockEntity;
import com.hbm.ntm.inventory.MachineShredderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

public final class MachineShredderScreen extends AbstractContainerScreen<MachineShredderMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "textures/gui/gui_shredder.png");
    private static final ResourceLocation GUI_UTILITY =
            ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "textures/gui/gui_utility.png");

    public MachineShredderScreen(MachineShredderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 233;
        inventoryLabelX = 8;
        inventoryLabelY = 139;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        if (isHovering(8, 18, 16, 88, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    List.of(Component.literal(menu.power() + "/" + MachineShredderBlockEntity.MAX_POWER + "HE")),
                    Optional.empty(), mouseX, mouseY);
        }
        if (hasBladeError() && mouseX >= leftPos - 16 && mouseX < leftPos
                && mouseY >= topPos + 36 && mouseY < topPos + 52) {
            graphics.renderTooltip(font,
                    List.of(Component.literal("Error: Shredder blades are broken or missing!")),
                    Optional.empty(), leftPos - 8, topPos + 52);
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

        if (menu.power() > 0) {
            int powerHeight = (int) (menu.power() * 88L / MachineShredderBlockEntity.MAX_POWER);
            graphics.blit(TEXTURE, leftPos + 8, topPos + 106 - powerHeight,
                    176, 160 - powerHeight, 16, powerHeight, 256, 256);
        }

        int progressWidth = menu.progress() * 34 / MachineShredderBlockEntity.PROCESSING_SPEED;
        graphics.blit(TEXTURE, leftPos + 63, topPos + 89, 176, 54,
                progressWidth + 1, 18, 256, 256);

        drawBlade(graphics, menu.bladeState(MachineShredderBlockEntity.BLADE_LEFT), true);
        drawBlade(graphics, menu.bladeState(MachineShredderBlockEntity.BLADE_RIGHT), false);

        if (hasBladeError()) {
            graphics.blit(GUI_UTILITY, leftPos - 16, topPos + 36, 8, 16, 16, 16, 256, 256);
        }
    }

    private void drawBlade(GuiGraphics graphics, int state, boolean left) {
        if (state == 0) {
            return;
        }
        int x = left ? 43 : 79;
        int sourceX = left ? 176 : 194;
        graphics.blit(TEXTURE, leftPos + x, topPos + 71,
                sourceX, (state - 1) * 18, 18, 18, 256, 256);
    }

    private boolean hasBladeError() {
        int left = menu.bladeState(MachineShredderBlockEntity.BLADE_LEFT);
        int right = menu.bladeState(MachineShredderBlockEntity.BLADE_RIGHT);
        return left == 0 || left == 3 || right == 0 || right == 3;
    }
}
