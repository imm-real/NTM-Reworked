package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.RefineryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

public final class RefineryScreen extends AbstractContainerScreen<RefineryMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/processing/gui_refinery.png");
    private static final ResourceLocation HEAVY_OIL_TEXTURE = fluidTexture("heavyoil");
    private static final ResourceLocation NAPHTHA_TEXTURE = fluidTexture("naphtha");
    private static final ResourceLocation LIGHT_OIL_TEXTURE = fluidTexture("lightoil");
    private static final ResourceLocation PETROLEUM_TEXTURE = fluidTexture("petroleum");

    private static final int INPUT_CAPACITY = 64_000;
    private static final int OUTPUT_CAPACITY = 24_000;
    private static final int HEAVY_OIL_COLOR = 0x141312;
    private static final int NAPHTHA_COLOR = 0x595744;
    private static final int LIGHT_OIL_COLOR = 0x8C7451;
    private static final int PETROLEUM_COLOR = 0x7CB7C9;

    public RefineryScreen(RefineryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 210;
        imageHeight = 231;
        inventoryLabelX = 8;
        inventoryLabelY = 139;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        if (isHovering(30, 27, 21, 104, mouseX, mouseY)) {
            tankTooltip(graphics, Component.translatable(menu.configuredFluid().translationKey()),
                    menu.inputAmount(), INPUT_CAPACITY, mouseX, mouseY);
        }
        if (isHovering(86, 42, 16, 52, mouseX, mouseY)) {
            tankTooltip(graphics, Component.translatable("hbmfluid.heavyoil"),
                    menu.heavyOilAmount(), OUTPUT_CAPACITY, mouseX, mouseY);
        }
        if (isHovering(106, 42, 16, 52, mouseX, mouseY)) {
            tankTooltip(graphics, Component.translatable("hbmfluid.naphtha"),
                    menu.naphthaAmount(), OUTPUT_CAPACITY, mouseX, mouseY);
        }
        if (isHovering(126, 42, 16, 52, mouseX, mouseY)) {
            tankTooltip(graphics, Component.translatable("hbmfluid.lightoil"),
                    menu.lightOilAmount(), OUTPUT_CAPACITY, mouseX, mouseY);
        }
        if (isHovering(146, 42, 16, 52, mouseX, mouseY)) {
            tankTooltip(graphics, Component.translatable("hbmfluid.petroleum"),
                    menu.petroleumAmount(), OUTPUT_CAPACITY, mouseX, mouseY);
        }
        if (isHovering(186, 18, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    List.of(Component.literal(menu.power() + "/" + menu.maxPower() + " HE")),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    private void tankTooltip(GuiGraphics graphics, Component fluid, int amount, int capacity,
                             int mouseX, int mouseY) {
        graphics.renderTooltip(font, List.of(fluid, Component.literal(amount + "/" + capacity + "mB")),
                Optional.empty(), mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 350, 256);

        int powerHeight = (int) (menu.power() * 50L / Math.max(menu.maxPower(), 1L));
        if (powerHeight > 0) {
            graphics.blit(TEXTURE, leftPos + 186, topPos + 69 - powerHeight,
                    210, 52 - powerHeight, 16, powerHeight, 350, 256);
        }

        int inputHeight = menu.inputAmount() * 101 / INPUT_CAPACITY;
        if (inputHeight > 0) {
            setColor(graphics, menu.configuredFluid().color());
            graphics.blit(TEXTURE, leftPos + 33, topPos + 130 - inputHeight,
                    226, 101 - inputHeight, 16, inputHeight, 350, 256);
            graphics.setColor(1F, 1F, 1F, 1F);
        }

        if (menu.hasRecipe()) {
            drawTintedBranch(graphics, HEAVY_OIL_COLOR, 52, 63, 247, 1, 33, 48);
            drawTintedBranch(graphics, NAPHTHA_COLOR, 52, 32, 247, 50, 66, 52);
            drawTintedBranch(graphics, LIGHT_OIL_COLOR, 52, 24, 247, 145, 86, 35);
            drawTintedBranch(graphics, PETROLEUM_COLOR, 36, 16, 211, 119, 122, 25);
        } else {
            drawPlainBranch(graphics, 52, 63, 247, 1, 33, 48);
            drawPlainBranch(graphics, 52, 32, 247, 50, 66, 52);
            drawPlainBranch(graphics, 52, 24, 247, 145, 86, 35);
            drawPlainBranch(graphics, 36, 16, 211, 119, 122, 25);
        }

        drawTank(graphics, HEAVY_OIL_TEXTURE, menu.heavyOilAmount(), leftPos + 86, topPos + 95);
        drawTank(graphics, NAPHTHA_TEXTURE, menu.naphthaAmount(), leftPos + 106, topPos + 95);
        drawTank(graphics, LIGHT_OIL_TEXTURE, menu.lightOilAmount(), leftPos + 126, topPos + 95);
        drawTank(graphics, PETROLEUM_TEXTURE, menu.petroleumAmount(), leftPos + 146, topPos + 95);
    }

    private void drawTintedBranch(GuiGraphics graphics, int color, int x, int y,
                                  int textureX, int textureY, int width, int height) {
        setColor(graphics, color);
        graphics.blit(TEXTURE, leftPos + x, topPos + y, textureX, textureY,
                width, height, 350, 256);
        graphics.setColor(1F, 1F, 1F, 1F);
    }

    private void drawPlainBranch(GuiGraphics graphics, int x, int y,
                                 int textureX, int textureY, int width, int height) {
        graphics.blit(TEXTURE, leftPos + x, topPos + y, textureX, textureY,
                width, height, 350, 256);
    }

    private static void drawTank(GuiGraphics graphics, ResourceLocation texture, int amount,
                                 int x, int bottom) {
        int remaining = amount * 52 / OUTPUT_CAPACITY;
        while (remaining > 0) {
            int strip = Math.min(16, remaining);
            bottom -= strip;
            graphics.blit(texture, x, bottom, 0, 16 - strip, 16, strip, 16, 16);
            remaining -= strip;
        }
    }

    private static void setColor(GuiGraphics graphics, int color) {
        graphics.setColor((color >> 16 & 0xFF) / 255F,
                (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, 1F);
    }

    private static ResourceLocation fluidTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "textures/gui/fluids/" + name + ".png");
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - 17 - font.width(title) / 2,
                6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                0x404040, false);
    }
}
