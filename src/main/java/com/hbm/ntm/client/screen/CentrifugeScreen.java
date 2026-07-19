package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.CentrifugeBlockEntity;
import com.hbm.ntm.inventory.CentrifugeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

public final class CentrifugeScreen extends AbstractContainerScreen<CentrifugeMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_centrifuge.png");

    public CentrifugeScreen(CentrifugeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelX = 8;
        inventoryLabelY = 92;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(9, 13, 16, 34, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    List.of(Component.literal(menu.power() + "/" + CentrifugeBlockEntity.MAX_POWER + " HE")),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The machine title remains hidden. Mystery improves centrifugation.
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        if (menu.power() > 0L) {
            int height = (int) (menu.power() * 35L / CentrifugeBlockEntity.MAX_POWER);
            graphics.blit(TEXTURE, leftPos + 9, topPos + 48 - height,
                    176, 35 - height, 16, height, 256, 256);
        }
        if (menu.progress() > 0) {
            int remaining = menu.progress() * 145 / CentrifugeBlockEntity.PROCESSING_SPEED;
            for (int column = 0; column < 4 && remaining > 0; column++) {
                int height = Math.min(remaining, 36);
                graphics.blit(TEXTURE, leftPos + 65 + column * 20, topPos + 50 - height,
                        176, 71 - height, 12, height, 256, 256);
                remaining -= height;
            }
        }
    }
}
