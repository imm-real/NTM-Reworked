package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.LargeNukeType;
import com.hbm.ntm.inventory.LargeNukeMenu;
import com.hbm.ntm.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public final class LargeNukeScreen extends AbstractContainerScreen<LargeNukeMenu> {
    private static final ResourceLocation GUI_UTILITY = texture("gui/gui_utility.png");
    private static final ResourceLocation MIKE = texture("gui/weapon/ivy_mike_schematic.png");

    public LargeNukeScreen(LargeNukeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = menu.type().guiWidth();
        imageHeight = menu.type().guiHeight();
        inventoryLabelX = menu.type().inventoryX();
        inventoryLabelY = menu.type().guiHeight() - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int infoX = leftPos - 16;
        int infoY = topPos + 16;
        if (mouseX >= infoX && mouseX < infoX + 16 && mouseY >= infoY && mouseY < infoY + 16) {
            List<Component> lines = new ArrayList<>();
            String prefix = "desc.gui.nuke" + switch (menu.type()) {
                case GADGET -> "Gadget";
                case BOY -> "Boy";
                case MIKE -> "Mike";
                case TSAR -> "Tsar";
            } + ".desc.";
            int count = switch (menu.type()) {
                case GADGET -> 5;
                case BOY -> 6;
                case MIKE -> 6;
                case TSAR -> 5;
            };
            for (int line = 0; line < count; line++) lines.add(Component.translatable(prefix + line));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int titleY = menu.type() == LargeNukeType.MIKE ? 4 : 6;
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, titleY, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation texture = texture("gui/weapon/" + switch (menu.type()) {
            case GADGET -> "gadget_schematic.png";
            case BOY -> "lil_boy_schematic.png";
            case MIKE -> "ivy_mike_schematic.png";
            case TSAR -> "tsar_bomba_schematic.png";
        });
        graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        switch (menu.type()) {
            case GADGET -> renderGadget(graphics, texture);
            case BOY -> renderBoy(graphics, texture);
            case MIKE -> renderMike(graphics, texture);
            case TSAR -> renderTsar(graphics, texture);
        }
        graphics.blit(GUI_UTILITY, leftPos - 16, topPos + 16, 8, 0, 16, 16, 256, 256);
    }

    private void renderGadget(GuiGraphics graphics, ResourceLocation texture) {
        if (menu.bombItem(1).is(ModItems.EARLY_EXPLOSIVE_LENSES.get())) blit(graphics, texture, 82, 19, 176, 0, 24, 24);
        if (menu.bombItem(2).is(ModItems.EARLY_EXPLOSIVE_LENSES.get())) blit(graphics, texture, 106, 19, 200, 0, 24, 24);
        if (menu.bombItem(3).is(ModItems.EARLY_EXPLOSIVE_LENSES.get())) blit(graphics, texture, 82, 43, 176, 24, 24, 24);
        if (menu.bombItem(4).is(ModItems.EARLY_EXPLOSIVE_LENSES.get())) blit(graphics, texture, 106, 43, 200, 24, 24, 24);
        if (menu.isReady()) blit(graphics, texture, 134, 35, 176, 48, 16, 16);
    }

    private void renderBoy(GuiGraphics graphics, ResourceLocation texture) {
        if (menu.isReady()) blit(graphics, texture, 142, 90, 176, 0, 16, 16);
        if (menu.bombItem(0).is(ModItems.BOY_SHIELDING.get())) blit(graphics, texture, 27, 87, 176, 16, 21, 22);
        if (menu.bombItem(1).is(ModItems.BOY_TARGET.get())) blit(graphics, texture, 27, 89, 176, 38, 21, 18);
        if (menu.bombItem(2).is(ModItems.BOY_BULLET.get())) blit(graphics, texture, 74, 94, 176, 57, 19, 8);
        if (menu.bombItem(3).is(ModItems.BOY_PROPELLANT.get())) blit(graphics, texture, 92, 95, 176, 66, 12, 6);
        if (menu.bombItem(4).is(ModItems.BOY_IGNITER.get())) blit(graphics, texture, 107, 91, 176, 75, 16, 14);
    }

    private void renderMike(GuiGraphics graphics, ResourceLocation texture) {
        if (menu.isReady() && !menu.isFilled()) blit(graphics, texture, 5, 35, 177, 1, 16, 16);
        if (menu.isFilled()) blit(graphics, texture, 5, 35, 177, 19, 16, 16);
        if (menu.bombItem(5).is(ModItems.MIKE_CORE.get())) blit(graphics, texture, 75, 25, 176, 49, 80, 36);
        if (menu.bombItem(6).is(ModItems.MIKE_DEUT.get())) blit(graphics, texture, 79, 30, 180, 88, 58, 26);
        if (menu.bombItem(7).is(ModItems.MIKE_COOLING_UNIT.get())) blit(graphics, texture, 140, 30, 240, 88, 12, 26);
        renderModernLenses(graphics, texture, 0, 0);
    }

    private void renderTsar(GuiGraphics graphics, ResourceLocation texture) {
        if (menu.isFilled()) blit(graphics, MIKE, 18, 50, 176, 18, 16, 16);
        else if (menu.isReady()) blit(graphics, MIKE, 18, 50, 176, 0, 16, 16);
        renderModernLenses(graphics, MIKE, 16, 16);
        if (menu.bombItem(5).is(ModItems.TSAR_CORE.get())) blit(graphics, MIKE, 91, 41, 176, 220, 80, 36);
    }

    private void renderModernLenses(GuiGraphics graphics, ResourceLocation texture, int offsetX, int offsetY) {
        int[][] regions = {{24, 20, 209, 1}, {24, 43, 209, 24}, {47, 20, 232, 1}, {47, 43, 232, 24}};
        for (int slot = 0; slot < 4; slot++) if (menu.bombItem(slot).is(ModItems.EXPLOSIVE_LENSES.get())) {
            int[] region = regions[slot];
            blit(graphics, texture, region[0] + offsetX, region[1] + offsetY, region[2], region[3], 23, 23);
        }
    }

    private void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        graphics.blit(texture, leftPos + x, topPos + y, u, v, width, height, 256, 256);
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "textures/" + path);
    }
}
