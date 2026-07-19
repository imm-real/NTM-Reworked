package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.DfcCoreBlockEntity;
import com.hbm.ntm.blockentity.DfcEmitterBlockEntity;
import com.hbm.ntm.blockentity.DfcInjectorBlockEntity;
import com.hbm.ntm.blockentity.DfcReceiverBlockEntity;
import com.hbm.ntm.dfc.DfcKind;
import com.hbm.ntm.inventory.DfcMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.network.DfcControlPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Five DFC control panels sharing one type-aware screen and one impending singularity. */
public final class DfcScreen extends AbstractContainerScreen<DfcMenu> {
    private EditBox wattsField;
    private boolean fieldSynced;

    public DfcScreen(DfcMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelX = 8;
        inventoryLabelY = 72;
    }

    @Override protected void init() {
        super.init();
        if (menu.kind() != DfcKind.EMITTER && menu.kind() != DfcKind.STABILIZER) return;
        int x = menu.kind() == DfcKind.EMITTER ? 57 : 75;
        wattsField = new EditBox(font, leftPos + x, topPos + 57, 29, 12, Component.empty());
        wattsField.setBordered(false);
        wattsField.setTextColor(0xFFFFFF);
        wattsField.setTextColorUneditable(0xFFFFFF);
        wattsField.setMaxLength(3);
        wattsField.setValue(Integer.toString(Math.max(menu.watts(), 1)));
        fieldSynced = menu.watts() > 0;
        addRenderableWidget(wattsField);
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (!fieldSynced && wattsField != null && menu.watts() > 0) {
            wattsField.setValue(Integer.toString(menu.watts()));
            fieldSynced = true;
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        switch (menu.kind()) {
            case CORE -> {
                tankTooltip(graphics, mouseX, mouseY, 26, menu.tank0(), selection(menu.tank0Type()),
                        DfcCoreBlockEntity.TANK_CAPACITY);
                tankTooltip(graphics, mouseX, mouseY, 134, menu.tank1(), selection(menu.tank1Type()),
                        DfcCoreBlockEntity.TANK_CAPACITY);
                if (isHovering(8, 17, 16, 52, mouseX, mouseY)) tooltip(graphics, mouseX, mouseY,
                        "Restriction Field: " + menu.field() + "%");
                if (isHovering(152, 17, 16, 52, mouseX, mouseY)) tooltip(graphics, mouseX, mouseY,
                        "Heat Saturation: " + menu.heat() + "%");
            }
            case EMITTER -> {
                tankTooltip(graphics, mouseX, mouseY, 8, menu.tank0(), FluidIdentifierItem.Selection.CRYOGEL,
                        DfcEmitterBlockEntity.CRYOGEL_CAPACITY);
                powerTooltip(graphics, mouseX, mouseY, 26);
            }
            case INJECTOR -> {
                tankTooltip(graphics, mouseX, mouseY, 44, menu.tank0(), selection(menu.tank0Type()),
                        DfcInjectorBlockEntity.TANK_CAPACITY);
                tankTooltip(graphics, mouseX, mouseY, 116, menu.tank1(), selection(menu.tank1Type()),
                        DfcInjectorBlockEntity.TANK_CAPACITY);
            }
            case RECEIVER -> tankTooltip(graphics, mouseX, mouseY, 8, menu.tank0(),
                    FluidIdentifierItem.Selection.CRYOGEL, DfcReceiverBlockEntity.CRYOGEL_CAPACITY);
            case STABILIZER -> powerTooltip(graphics, mouseX, mouseY, 35);
        }
    }

    private void tankTooltip(GuiGraphics graphics, int mouseX, int mouseY, int x, int amount,
                             FluidIdentifierItem.Selection fluid, int capacity) {
        if (!isHovering(x, 17, 16, 52, mouseX, mouseY)) return;
        graphics.renderTooltip(font, List.of(Component.translatable(fluid.translationKey()),
                        Component.literal(amount + "/" + capacity + "mB")), Optional.empty(), mouseX, mouseY);
    }

    private void powerTooltip(GuiGraphics graphics, int mouseX, int mouseY, int x) {
        if (isHovering(x, 17, 16, 52, mouseX, mouseY)) tooltip(graphics, mouseX, mouseY,
                menu.power() + "/" + menu.maxPower() + " HE");
    }

    private void tooltip(GuiGraphics graphics, int mouseX, int mouseY, String text) {
        graphics.renderTooltip(font, Component.literal(text), mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "textures/gui/dfc/gui_" + menu.kind().name().toLowerCase(Locale.ROOT) + ".png");
        graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        switch (menu.kind()) {
            case CORE -> {
                verticalBar(graphics, texture, 8, menu.field(), 100, 176);
                verticalBar(graphics, texture, 152, menu.heat(), 100, 192);
                drawFluid(graphics, selection(menu.tank0Type()), menu.tank0(), 26, DfcCoreBlockEntity.TANK_CAPACITY);
                drawFluid(graphics, selection(menu.tank1Type()), menu.tank1(), 134, DfcCoreBlockEntity.TANK_CAPACITY);
            }
            case EMITTER -> {
                verticalBar(graphics, texture, 26, menu.power(), menu.maxPower(), 176);
                drawFluid(graphics, FluidIdentifierItem.Selection.CRYOGEL, menu.tank0(), 8,
                        DfcEmitterBlockEntity.CRYOGEL_CAPACITY);
                graphics.blit(texture, leftPos + 53, topPos + 45, 210, 0,
                        Math.clamp(menu.watts(), 0, 100) * 34 / 100, 4, 256, 256);
                if (wattsField != null && wattsField.isFocused())
                    graphics.blit(texture, leftPos + 53, topPos + 53, 210, 4, 34, 16, 256, 256);
                if (menu.on()) graphics.blit(texture, leftPos + 133, topPos + 52, 192, 0, 18, 18, 256, 256);
            }
            case INJECTOR -> {
                drawFluid(graphics, selection(menu.tank0Type()), menu.tank0(), 44, DfcInjectorBlockEntity.TANK_CAPACITY);
                drawFluid(graphics, selection(menu.tank1Type()), menu.tank1(), 116, DfcInjectorBlockEntity.TANK_CAPACITY);
            }
            case RECEIVER -> drawFluid(graphics, FluidIdentifierItem.Selection.CRYOGEL, menu.tank0(), 8,
                    DfcReceiverBlockEntity.CRYOGEL_CAPACITY);
            case STABILIZER -> {
                verticalBar(graphics, texture, 35, menu.power(), menu.maxPower(), 176);
                graphics.blit(texture, leftPos + 71, topPos + 45, 192, 0,
                        Math.clamp(menu.watts(), 0, 100) * 34 / 100, 4, 256, 256);
                if (wattsField != null && wattsField.isFocused())
                    graphics.blit(texture, leftPos + 71, topPos + 53, 192, 4, 34, 16, 256, 256);
            }
        }
    }

    private void verticalBar(GuiGraphics graphics, ResourceLocation texture, int x,
                             long amount, long capacity, int u) {
        int height = (int) (Math.max(amount, 0L) * 52L / Math.max(capacity, 1L));
        height = Math.clamp(height, 0, 52);
        if (height > 0) graphics.blit(texture, leftPos + x, topPos + 69 - height,
                u, 52 - height, 16, height, 256, 256);
    }

    private void drawFluid(GuiGraphics graphics, FluidIdentifierItem.Selection fluid, int amount,
                           int x, int capacity) {
        if (fluid == FluidIdentifierItem.Selection.NONE || amount <= 0) return;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "textures/gui/fluids/" + fluid.id() + ".png");
        int remaining = Math.clamp(amount * 52 / capacity, 0, 52);
        int bottom = topPos + 69;
        while (remaining > 0) {
            int strip = Math.min(16, remaining);
            bottom -= strip;
            graphics.blit(texture, leftPos + x, bottom, 0, 16 - strip, 16, strip, 16, 16);
            remaining -= strip;
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        if (menu.kind() == DfcKind.EMITTER) graphics.drawString(font,
                "Output: " + shortNumber(menu.signal()) + "Spk", 50, 30, 0xFF7F7F, false);
        if (menu.kind() == DfcKind.RECEIVER) {
            graphics.drawString(font, "Input:", 40, 25, 0xFF7F7F, false);
            graphics.drawString(font, shortNumber(menu.signal()) + "Spk", 50, 35, 0xFF7F7F, false);
            graphics.drawString(font, "Output:", 40, 45, 0xFF7F7F, false);
            graphics.drawString(font, shortNumber(menu.signal() * 5_000L) + "HE", 50, 55, 0xFF7F7F, false);
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && menu.kind() == DfcKind.EMITTER
                && inside(mouseX, mouseY, 97, 52, 18, 18)) {
            send(false);
            return true;
        }
        if (button == 0 && menu.kind() == DfcKind.EMITTER
                && inside(mouseX, mouseY, 133, 52, 18, 18)) {
            send(true);
            return true;
        }
        if (button == 0 && menu.kind() == DfcKind.STABILIZER
                && inside(mouseX, mouseY, 124, 52, 18, 18)) {
            send(false);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width
                && mouseY > topPos + y && mouseY <= topPos + y + height;
    }

    private void send(boolean toggle) {
        int watts = parseWatts();
        if (wattsField != null) wattsField.setValue(Integer.toString(watts));
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
        PacketDistributor.sendToServer(new DfcControlPayload(watts, toggle));
    }

    private int parseWatts() {
        if (wattsField == null) return Math.clamp(menu.watts(), 1, 100);
        try { return Math.clamp(Integer.parseInt(wattsField.getValue()), 1, 100); }
        catch (NumberFormatException ignored) { return 1; }
    }

    private static FluidIdentifierItem.Selection selection(int ordinal) {
        FluidIdentifierItem.Selection[] values = FluidIdentifierItem.Selection.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FluidIdentifierItem.Selection.NONE;
    }

    private static String shortNumber(long value) {
        long absolute = Math.abs(value);
        if (absolute < 1_000L) return Long.toString(value);
        if (absolute < 1_000_000L) return String.format(Locale.ROOT, "%.1fk", value / 1_000.0D);
        if (absolute < 1_000_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0D);
        return String.format(Locale.ROOT, "%.1fG", value / 1_000_000_000.0D);
    }
}
