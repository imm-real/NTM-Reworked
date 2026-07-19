package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.inventory.ResearchReactorMenu;
import com.hbm.ntm.registry.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Optional;

/** A 176x222 reactor panel with an invisible field and very visible consequences. */
public final class ResearchReactorScreen extends AbstractContainerScreen<ResearchReactorMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/reactors/gui_research_reactor.png");
    private static final ResourceLocation GUI_UTILITY = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/gui_utility.png");
    private EditBox field;
    private int pressedTicks;

    public ResearchReactorScreen(ResearchReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelX = 8;
        inventoryLabelY = 128;
    }

    @Override
    protected void init() {
        super.init();
        field = new EditBox(font, leftPos + 8, topPos + 99, 33, 16, Component.empty()) {
            @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
        };
        field.setBordered(false);
        field.setMaxLength(3);
        field.setFilter(text -> text.matches("[0-9]{0,3}"));
        field.setValue(Integer.toString((int) (menu.level() * 100.0D)));
        addRenderableWidget(field);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (pressedTicks > 0) pressedTicks--;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(-14, 23, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.literal("The reactor has to be submerged"),
                    Component.literal("in water on its sides to cool."),
                    Component.literal("The neutron flux is provided to"),
                    Component.literal("adjacent breeding reactors.")), Optional.empty(), leftPos - 6, topPos + 39);
        } else if (isHovering(-14, 61, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(
                    Component.literal("This reactor is fueled with plate fuel."),
                    Component.literal("The reaction needs a neutron source to start.")),
                    Optional.empty(), leftPos - 6, topPos + 77);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        if (menu.level() <= 0.5D) {
            for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) {
                graphics.blit(TEXTURE, leftPos + 81 + 36 * x, topPos + 26 + 36 * y,
                        176, 0, 8, 8, 256, 256);
            }
            for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
                graphics.blit(TEXTURE, leftPos + 99 + 36 * x, topPos + 44 + 36 * y,
                        176, 0, 8, 8, 256, 256);
            }
        }
        if (pressedTicks > 0) {
            graphics.blit(TEXTURE, leftPos + 44, topPos + 97, 176, 8, 11, 20, 256, 256);
        }

        sevenSegment(graphics, menu.totalFlux(), 4, 14, 25, false);
        sevenSegment(graphics, menu.temperature(), 3, 12, 63, false);
        int control = parsedControl();
        sevenSegment(graphics, control, 3, 5, 101,
                field != null && field.isFocused() && minecraft != null && minecraft.level != null
                        && minecraft.level.getGameTime() % 20L >= 10L);

        graphics.blit(GUI_UTILITY, leftPos - 14, topPos + 23, 24, 0, 16, 16, 256, 256);
        graphics.blit(GUI_UTILITY, leftPos - 14, topPos + 61, 8, 0, 16, 16, 256, 256);
    }

    private int parsedControl() {
        if (field == null || field.getValue().isEmpty()) return 0;
        try {
            int value = Math.clamp(Integer.parseInt(field.getValue()), 0, 100);
            String clamped = Integer.toString(value);
            if (!field.getValue().equals(clamped)) field.setValue(clamped);
            return value;
        } catch (NumberFormatException ignored) {
            field.setValue("0");
            return 0;
        }
    }

    private void sevenSegment(GuiGraphics graphics, int value, int digits, int x, int y, boolean hidden) {
        if (hidden) return;
        String text = Integer.toString(Math.max(value, 0));
        int gap = digits - text.length();
        for (int index = 0; index < text.length(); index++) {
            int offset = 9 * (index + gap);
            digit(graphics, text.charAt(index), leftPos + x + offset, topPos + y);
        }
    }

    private void digit(GuiGraphics graphics, char digit, int x, int y) {
        int mask = switch (digit) {
            case '0' -> 0b1110111; case '1' -> 0b0100100; case '2' -> 0b1011101;
            case '3' -> 0b1101101; case '4' -> 0b0101110; case '5' -> 0b1101011;
            case '6' -> 0b1111011; case '7' -> 0b0100101; case '8' -> 0b1111111;
            case '9' -> 0b1101111; default -> 0b1011011;
        };
        int color = 0xFF08FF00;
        if ((mask & 1) != 0) graphics.fill(x + 1, y, x + 5, y + 1, color);
        if ((mask & 2) != 0) graphics.fill(x, y + 1, x + 1, y + 6, color);
        if ((mask & 4) != 0) graphics.fill(x + 5, y + 1, x + 6, y + 6, color);
        if ((mask & 8) != 0) graphics.fill(x + 1, y + 6, x + 5, y + 7, color);
        if ((mask & 16) != 0) graphics.fill(x, y + 7, x + 1, y + 12, color);
        if ((mask & 32) != 0) graphics.fill(x + 5, y + 7, x + 6, y + 12, color);
        if ((mask & 64) != 0) graphics.fill(x + 1, y + 12, x + 5, y + 13, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 121 - font.width(title) / 2, 6, 0xE5E5E5, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        graphics.drawString(font, "Flux", 6, 13, 0xE5E5E5, false);
        graphics.drawString(font, "Heat", 6, 51, 0xE5E5E5, false);
        graphics.drawString(font, "Control", 6, 89, 0xE5E5E5, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= leftPos + 44 && mouseX < leftPos + 55
                && mouseY > topPos + 97 && mouseY <= topPos + 117
                && minecraft != null && minecraft.gameMode != null) {
            int level = parsedControl();
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, level);
            pressedTicks = 15;
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(ModSounds.RESEARCH_REACTOR_COVER.get(), 0.5F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
