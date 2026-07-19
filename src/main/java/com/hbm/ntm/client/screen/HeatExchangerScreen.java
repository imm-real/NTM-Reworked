package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.HeatExchangerBlockEntity;
import com.hbm.ntm.inventory.HeatExchangerMenu;
import com.hbm.ntm.item.FluidIdentifierItem;
import com.hbm.ntm.network.HeatExchangerControlPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

public final class HeatExchangerScreen extends AbstractContainerScreen<HeatExchangerMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/machine/gui_heatex.png");
    private EditBox amountField;
    private EditBox delayField;
    private boolean suppressControl;
    private boolean controlsSynced;

    public HeatExchangerScreen(HeatExchangerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 204;
        inventoryLabelX = 8;
        inventoryLabelY = 110;
    }

    @Override protected void init() {
        super.init();
        suppressControl = true;
        amountField = field(leftPos + 73, topPos + 31, Math.max(menu.amountToCool(), 1));
        delayField = field(leftPos + 73, topPos + 49, Math.max(menu.tickDelay(), 1));
        controlsSynced = menu.amountToCool() > 0 && menu.tickDelay() > 0;
        suppressControl = false;
        amountField.setResponder(value -> sendControls());
        delayField.setResponder(value -> sendControls());
        addRenderableWidget(amountField);
        addRenderableWidget(delayField);
    }

    private EditBox field(int x, int y, int initial) {
        EditBox field = new EditBox(font, x, y, 30, 10, Component.empty());
        field.setBordered(false);
        field.setTextColor(0x00FF00);
        field.setTextColorUneditable(0x00FF00);
        field.setMaxLength(5);
        field.setValue(Integer.toString(initial));
        return field;
    }

    private void sendControls() {
        if (suppressControl || amountField == null || delayField == null) return;
        controlsSynced = true;
        PacketDistributor.sendToServer(new HeatExchangerControlPayload(
                parse(amountField.getValue()), parse(delayField.getValue())));
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (controlsSynced || amountField == null || delayField == null
                || menu.amountToCool() <= 0 || menu.tickDelay() <= 0) return;
        suppressControl = true;
        amountField.setValue(Integer.toString(menu.amountToCool()));
        delayField.setValue(Integer.toString(menu.tickDelay()));
        suppressControl = false;
        controlsSynced = true;
    }

    private static int parse(String text) {
        try {
            return Math.max(Integer.parseInt(text), 1);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(44, 36, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.translatable(menu.inputSelection().translationKey()),
                            Component.literal(menu.inputAmount() + "/" + HeatExchangerBlockEntity.TANK_CAPACITY + "mB")),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(116, 36, 16, 52, mouseX, mouseY)) {
            graphics.renderTooltip(font, List.of(Component.translatable(menu.outputSelection().translationKey()),
                            Component.literal(menu.outputAmount() + "/" + HeatExchangerBlockEntity.TANK_CAPACITY + "mB")),
                    Optional.empty(), mouseX, mouseY);
        } else if (isHovering(70, 26, 36, 18, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal("Amount per cycle"), mouseX, mouseY);
        } else if (isHovering(70, 44, 36, 18, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.literal("Cycle tick delay"), mouseX, mouseY);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        drawFluid(graphics, menu.inputSelection(), menu.inputAmount(), leftPos + 44, topPos + 36);
        drawFluid(graphics, menu.outputSelection(), menu.outputAmount(), leftPos + 116, topPos + 36);
    }

    private void drawFluid(GuiGraphics graphics, FluidIdentifierItem.Selection selection, int amount,
                           int x, int y) {
        if (selection == FluidIdentifierItem.Selection.NONE || amount <= 0) return;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID,
                "textures/gui/fluids/" + selection.id() + ".png");
        int remaining = amount * 52 / HeatExchangerBlockEntity.TANK_CAPACITY;
        int bottom = y + 52;
        while (remaining > 0) {
            int strip = Math.min(16, remaining);
            bottom -= strip;
            graphics.blit(texture, x, bottom, 0, 16 - strip, 16, strip, 16, 16);
            remaining -= strip;
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
