package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.block.RadioTorchBlock;
import com.hbm.ntm.blockentity.RadioTorchBlockEntity;
import com.hbm.ntm.inventory.RadioTorchMenu;
import com.hbm.ntm.network.RadioTorchConfigPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class RadioTorchScreen extends AbstractContainerScreen<RadioTorchMenu> {
    private final List<EditBox> channels = new ArrayList<>(), names = new ArrayList<>(), mappings = new ArrayList<>();
    private final byte[] conditions = new byte[16];
    private boolean polling, custom, descending;

    public RadioTorchScreen(RadioTorchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = menu.kind() == RadioTorchBlock.Kind.COUNTER ? 218 : 256;
        imageHeight = menu.kind() == RadioTorchBlock.Kind.COUNTER ? 238 : (menu.kind() == RadioTorchBlock.Kind.CONTROLLER ? 42 : 204);
        inventoryLabelX = 8;
        inventoryLabelY = 144;
    }

    @Override protected void init() {
        super.init();
        RadioTorchBlockEntity radio = menu.blockEntity();
        polling = radio != null && radio.polling(); custom = radio != null && radio.customMapping(); descending = radio != null && radio.descending();
        for (int i = 0; i < 16; i++) conditions[i] = (byte) (radio == null ? 0 : radio.condition(i));
        switch (menu.kind()) {
            case SENDER, RECEIVER -> initMapping(radio);
            case LOGIC -> initLogic(radio);
            case READER -> initReader(radio);
            case CONTROLLER -> channels.add(field(29, 21, 82, 14, 15, radio == null ? "" : radio.channel(0)));
            case COUNTER -> {
                for (int i = 0; i < 3; i++) channels.add(field(29, 21 + i * 44, 86, 14, 10, radio == null ? "" : radio.channel(i)));
            }
        }
    }

    private void initMapping(RadioTorchBlockEntity radio) {
        channels.add(field(29, 21, 82, 14, 15, radio == null ? "" : radio.channel(0)));
        int inset = menu.kind() == RadioTorchBlock.Kind.SENDER ? 18 : 0;
        for (int i = 0; i < 16; i++) {
            EditBox field = field(11 + 130 * (i / 8) + inset, 57 + 18 * (i % 8), 82, 14, 32,
                    radio == null ? "" : radio.mapping(i));
            field.visible = custom;
            mappings.add(field);
        }
    }

    private void initLogic(RadioTorchBlockEntity radio) {
        channels.add(field(29, 21, 82, 14, 15, radio == null ? "" : radio.channel(0)));
        for (int i = 0; i < 16; i++) {
            mappings.add(field(29 + 130 * (i / 8), 57 + 18 * (i % 8), 46, 14, 15,
                    radio == null ? "" : radio.mapping(i)));
        }
    }

    private void initReader(RadioTorchBlockEntity radio) {
        for (int i = 0; i < 8; i++) {
            channels.add(field(29, 57 + i * 18, 64, 14, 15, radio == null ? "" : radio.channel(i)));
            names.add(field(123, 57 + i * 18, 118, 14, 25, radio == null ? "" : radio.name(i)));
        }
    }

    private EditBox field(int x, int y, int width, int height, int max, String value) {
        EditBox field = new EditBox(font, leftPos + x, topPos + y, width, height, Component.empty());
        field.setBordered(false); field.setTextColor(0x00FF00); field.setTextColorUneditable(0x00FF00);
        field.setMaxLength(max); field.setValue(value);
        addRenderableWidget(field); return field;
    }

    @Override public void removed() {
        sendConfig();
        super.removed();
    }

    private void sendConfig() {
        String[] channelValues = values(channels, 8), nameValues = values(names, 8), mappingValues = values(mappings, 16);
        PacketDistributor.sendToServer(new RadioTorchConfigPayload(polling, custom, descending,
                channelValues, nameValues, mappingValues, conditions));
    }

    private static String[] values(List<EditBox> fields, int count) {
        String[] values = new String[count];
        for (int i = 0; i < count; i++) values[i] = i < fields.size() ? fields.get(i).getValue() : "";
        return values;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(HbmNtm.MOD_ID, "textures/gui/machine/gui_rtty_" + switch (menu.kind()) {
            case SENDER -> "sender"; case RECEIVER -> "receiver"; case COUNTER -> "counter";
            case LOGIC -> "logic_receiver"; case READER -> "reader"; case CONTROLLER -> "controller";
        } + ".png");
        if ((menu.kind() == RadioTorchBlock.Kind.SENDER || menu.kind() == RadioTorchBlock.Kind.RECEIVER) && !custom) {
            graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, 35, 256, 256);
            graphics.blit(texture, leftPos, topPos + 35, 0, 197, imageWidth, 7, 256, 256);
        } else {
            graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        }
        if (polling) {
            if (menu.kind() == RadioTorchBlock.Kind.COUNTER)
                graphics.blit(texture, leftPos + 193, topPos + 8, 218, 0, 18, 18, 256, 256);
            else if (menu.kind() == RadioTorchBlock.Kind.CONTROLLER)
                graphics.blit(texture, leftPos + 173, topPos + 17, 0, 42, 18, 18, 256, 256);
            else
                graphics.blit(texture, leftPos + 173, topPos + 17, 0,
                        menu.kind() == RadioTorchBlock.Kind.READER ? 204 : 222, 18, 18, 256, 256);
        }
        if (custom && (menu.kind() == RadioTorchBlock.Kind.SENDER || menu.kind() == RadioTorchBlock.Kind.RECEIVER))
            graphics.blit(texture, leftPos + 137, topPos + 17, 0, 204, 18, 18, 256, 256);
        if (descending && menu.kind() == RadioTorchBlock.Kind.LOGIC)
            graphics.blit(texture, leftPos + 137, topPos + 17, 0, 204, 18, 18, 256, 256);
        if (menu.kind() == RadioTorchBlock.Kind.LOGIC) {
            for (int i = 0; i < 16; i++) graphics.blit(texture,
                    leftPos + 7 + 130 * (i / 8), topPos + 53 + 18 * (i % 8),
                    18 + conditions[i] * 18, 204, 18, 18, 256, 256);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        int x = (int) mouseX - leftPos, y = (int) mouseY - topPos;
        if (menu.kind() == RadioTorchBlock.Kind.COUNTER) {
            if (inside(x, y, 193, 8, 18, 18)) polling = true;
            if (inside(x, y, 193, 30, 18, 18)) sendConfig();
            return handled;
        }
        if (menu.kind() == RadioTorchBlock.Kind.SENDER || menu.kind() == RadioTorchBlock.Kind.RECEIVER) {
            if (inside(x, y, 137, 17, 18, 18)) {
                custom = !custom; mappings.forEach(field -> field.visible = custom);
            }
        } else if (menu.kind() == RadioTorchBlock.Kind.LOGIC && inside(x, y, 137, 17, 18, 18)) {
            descending = !descending;
        }
        if (inside(x, y, 173, 17, 18, 18)) polling = !polling;
        if (inside(x, y, 209, 17, 18, 18)) sendConfig();
        if (menu.kind() == RadioTorchBlock.Kind.LOGIC) {
            for (int i = 0; i < 16; i++) if (inside(x, y, 7 + 130 * (i / 8), 53 + 18 * (i % 8), 18, 18))
                conditions[i] = (byte) ((conditions[i] + 1) % 10);
        }
        return handled;
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
        if (menu.kind() == RadioTorchBlock.Kind.COUNTER && menu.blockEntity() != null) {
            for (int i = 0; i < 3; i++) if (isHovering(137, 17 + i * 44, 18, 18, mouseX, mouseY)) {
                graphics.renderTooltip(font, List.of(Component.literal(menu.blockEntity().patternMode(i)),
                        Component.literal("Right-click to change match mode")), java.util.Optional.empty(), mouseX, mouseY);
            }
        } else if (inside(mouseX - leftPos, mouseY - topPos, 209, 17, 18, 18)) {
            graphics.renderTooltip(font, Component.literal("Save Settings"), mouseX, mouseY);
        } else if (inside(mouseX - leftPos, mouseY - topPos, 173, 17, 18, 18)) {
            graphics.renderTooltip(font, Component.literal(polling ? "Polling" : "State Change"), mouseX, mouseY);
        } else if ((menu.kind() == RadioTorchBlock.Kind.SENDER || menu.kind() == RadioTorchBlock.Kind.RECEIVER)
                && inside(mouseX - leftPos, mouseY - topPos, 137, 17, 18, 18)) {
            graphics.renderTooltip(font, Component.literal(custom ? "Custom Mapping" : "Redstone Passthrough"), mouseX, mouseY);
        } else if (menu.kind() == RadioTorchBlock.Kind.LOGIC
                && inside(mouseX - leftPos, mouseY - topPos, 137, 17, 18, 18)) {
            graphics.renderTooltip(font, Component.literal(descending ? "Descending" : "Ascending"), mouseX, mouseY);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        if (menu.kind() == RadioTorchBlock.Kind.COUNTER)
            graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
