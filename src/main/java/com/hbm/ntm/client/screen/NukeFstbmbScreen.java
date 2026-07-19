package com.hbm.ntm.client.screen;

import com.hbm.ntm.HbmNtm;
import com.hbm.ntm.blockentity.NukeBalefireBlockEntity;
import com.hbm.ntm.inventory.NukeFstbmbMenu;
import com.hbm.ntm.network.NukeFstbmbButtonPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Source {@code GUINukeFstbmb}: schematic background, timer field, start button, and live mm:ss readout. */
public final class NukeFstbmbScreen extends AbstractContainerScreen<NukeFstbmbMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HbmNtm.MOD_ID, "textures/gui/weapon/fstbmb_schematic.png");

    private EditBox timerField;
    private boolean suppress;

    public NukeFstbmbScreen(NukeFstbmbMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = imageHeight - 96 + 2;
    }

    @Override
    protected void init() {
        super.init();
        suppress = true;
        // Source GuiTextField at (guiLeft+94, guiTop+40), 29x12, red text, no background, max 3 chars.
        timerField = new EditBox(font, leftPos + 94, topPos + 40, 29, 12, Component.empty());
        timerField.setBordered(false);
        timerField.setTextColor(0xff0000);
        timerField.setTextColorUneditable(0x800000);
        timerField.setMaxLength(3);
        timerField.setFilter(text -> text.chars().allMatch(Character::isDigit));
        timerField.setValue(String.valueOf(bombTimer() / 20));
        timerField.setResponder(this::onTimerChanged);
        addRenderableWidget(timerField);
        suppress = false;
    }

    private void onTimerChanged(String value) {
        if (suppress || value.isEmpty()) return;
        try {
            int seconds = Mth.clamp(Integer.parseInt(value), 1, 999);
            PacketDistributor.sendToServer(new NukeFstbmbButtonPayload(seconds, 1));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        NukeBalefireBlockEntity bomb = menu.blockEntity();
        // Source start-button hitbox: x[guiLeft+142..+160), y(guiTop+35..+53], only when not started.
        if (bomb != null && !bomb.started()
                && mouseX >= leftPos + 142 && mouseX < leftPos + 160
                && mouseY > topPos + 35 && mouseY <= topPos + 53) {
            PacketDistributor.sendToServer(new NukeFstbmbButtonPayload(0, 0));
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int bombTimer() {
        NukeBalefireBlockEntity bomb = menu.blockEntity();
        return bomb != null ? bomb.timer() : 18000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        NukeBalefireBlockEntity bomb = menu.blockEntity();
        if (bomb == null) return;

        if (bomb.hasEgg()) {
            graphics.blit(TEXTURE, leftPos + 19, topPos + 90, 176, 0, 30, 16, 256, 256);
        }
        int battery = bomb.getBattery();
        if (battery == 1) {
            graphics.blit(TEXTURE, leftPos + 88, topPos + 93, 176, 16, 18, 10, 256, 256);
        } else if (battery == 2) {
            graphics.blit(TEXTURE, leftPos + 88, topPos + 93, 194, 16, 18, 10, 256, 256);
        }
        if (bomb.started()) {
            graphics.blit(TEXTURE, leftPos + 142, topPos + 35, 176, 26, 18, 18, 256, 256);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);

        NukeBalefireBlockEntity bomb = menu.blockEntity();
        if (bomb != null && bomb.hasBattery()) {
            String time = bomb.getMinutes() + ":" + bomb.getSeconds();
            double scale = 0.75;
            graphics.pose().pushPose();
            graphics.pose().scale((float) scale, (float) scale, 1.0F);
            graphics.drawString(font, time, (int) ((69 - font.width(time) / 2.0F) * (1 / scale)),
                    (int) (95.5 * (1 / scale)), 0xff0000, false);
            graphics.pose().popPose();
        }
    }
}
